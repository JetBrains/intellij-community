// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitCommittedChangeList;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.repo.*;
import git4idea.util.GitSimplePathsBrowser;
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static java.util.Collections.emptyList;

/**
 * Git utility/helper methods
 */
public final class GitUtil {
  public static final @NonNls String DOT_GIT = ".git";

  /**
   * This comment char overrides the standard '#' and any other potentially defined by user via {@code core.commentChar}.
   */
  public static final @NonNls String COMMENT_CHAR = "\u0001";

  public static final @NonNls String ORIGIN_HEAD = "origin/HEAD";

  public static final @NlsSafe String HEAD = "HEAD";
  public static final @NonNls String CHERRY_PICK_HEAD = "CHERRY_PICK_HEAD";
  public static final @NonNls String MERGE_HEAD = "MERGE_HEAD";
  public static final @NonNls String REBASE_HEAD = "REBASE_HEAD";

  private static final @NonNls String REPO_PATH_LINK_PREFIX = "gitdir:";
  private static final Logger LOG = Logger.getInstance(GitUtil.class);
  private static final @NonNls String HEAD_FILE = "HEAD";

  private static final Pattern HASH_STRING_PATTERN = Pattern.compile("[a-fA-F0-9]{40}");

  /**
   * A private constructor to suppress instance creation
   */
  private GitUtil() {
    // do nothing
  }

  public static void updateHead(@NotNull GitRepository repository,
                                @NotNull Hash newObjectId,
                                @Nullable String reflogMessage) throws VcsException {
    Git.getInstance().updateReference(repository, HEAD, newObjectId, reflogMessage).throwOnError();
  }

  /**
   * Returns the Git repository location for the given repository root directory, or null if nothing can be found.
   * Able to find the real repository root of a submodule or of a working tree.
   * <p/>
   * More precisely: checks if there is {@code .git} directory or file directly under rootDir.<br/>
   * If there is a directory, performs a quick check that it looks like a Git repository;<br/>
   * if it is a file, follows the path written inside this file to find the actual repo dir ('git worktree').
   *
   * @return Directory containing the {@link GitRepositoryFiles#HEAD}/{@link GitRepositoryFiles#INDEX}/etc. files
   * @see #isGitRoot(Path)
   */
  public static @Nullable VirtualFile findGitDir(@NotNull VirtualFile rootDir) {
    VirtualFile dotGit = VfsUtil.refreshAndFindChild(rootDir, DOT_GIT);
    if (dotGit == null) {
      return null;
    }
    if (dotGit.isDirectory()) {
      boolean headExists = VfsUtil.refreshAndFindChild(dotGit, HEAD_FILE) != null;
      return headExists ? dotGit : null;
    }

    // if .git is a file with some specific content, it indicates a submodule or a working tree, with a link to the real repository path
    String content = readContent(dotGit);
    if (content == null) return null;
    String pathToDir = parsePathToRepository(content);
    if (pathToDir == null) return null;
    Path file = findRealRepositoryDir(rootDir.toNioPath(), pathToDir);
    if (file == null) return null;
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
  }

  private static @Nullable Path findRealRepositoryDir(@NotNull @NonNls Path rootPath, @NotNull @NonNls String path) {
    if (!FileUtil.isAbsolute(path)) {
      String canonicalPath = FileUtil.toCanonicalPath(FileUtil.join(rootPath.toString(), path), true);
      path = FileUtil.toSystemIndependentName(canonicalPath);
    }

    Path file = Path.of(path);
    if (!Files.isDirectory(file)) {
      return null;
    }
    return file;
  }

  @ApiStatus.Internal
  public static @Nullable String parsePathToRepository(@NotNull @NonNls String content) {
    content = content.trim();
    if (content.startsWith(REPO_PATH_LINK_PREFIX)) {
      content = content.substring(REPO_PATH_LINK_PREFIX.length()).trim();
    }
    if (content.isEmpty() || content.contains("\n")) return null;
    return content;
  }

  private static @Nullable String readContent(@NotNull VirtualFile dotGit) {
    String content;
    try {
      content = readFile(dotGit);
    }
    catch (IOException e) {
      LOG.error("Couldn't read the content of " + dotGit, e);
      return null;
    }
    return content;
  }

  /**
   * Makes 3 attempts to get the contents of the file. If all 3 fail with an IOException, rethrows the exception.
   */
  private static @NotNull String readFile(@NotNull VirtualFile file) throws IOException {
    final int ATTEMPTS = 3;
    int attempt = 1;
    while (true) {
      try {
        return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
      }
      catch (IOException e) {
        LOG.info(String.format("IOException while reading %s (attempt #%s)", file, attempt));
        if (attempt++ >= ATTEMPTS) {
          throw e;
        }
      }
    }
  }

  /**
   * @throws VcsException if non git files are passed
   */
  @RequiresBackgroundThread
  public static @NotNull Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Project project,
                                                                                @NotNull Collection<? extends VirtualFile> virtualFiles)
    throws VcsException {
    return sortFilesByGitRoot(project, virtualFiles, false);
  }

  @RequiresBackgroundThread
  public static @NotNull Map<VirtualFile, List<VirtualFile>> sortFilesByGitRootIgnoringMissing(@NotNull Project project,
                                                                                               @NotNull Collection<? extends VirtualFile> filePaths) {
    try {
      return sortFilesByGitRoot(project, filePaths, true);
    }
    catch (VcsException e) {
      LOG.error(new IllegalArgumentException(e));
      return Collections.emptyMap();
    }
  }

  /**
   * @throws VcsException if non git files are passed
   */
  @RequiresBackgroundThread
  public static @NotNull Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Project project,
                                                                                 @NotNull Collection<? extends FilePath> filePaths)
    throws VcsException {
    return sortFilePathsByGitRoot(project, filePaths, false);
  }

  @RequiresBackgroundThread
  public static @NotNull Map<VirtualFile, List<FilePath>> sortFilePathsByGitRootIgnoringMissing(@NotNull Project project,
                                                                                                @NotNull Collection<? extends FilePath> filePaths) {
    try {
      return sortFilePathsByGitRoot(project, filePaths, true);
    }
    catch (VcsException e) {
      LOG.error(new IllegalArgumentException(e));
      return Collections.emptyMap();
    }
  }

  @RequiresBackgroundThread
  private static @NotNull Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Project project,
                                                                                 @NotNull Collection<? extends VirtualFile> virtualFiles,
                                                                                 boolean ignoreNonGit)
    throws VcsException {
    Map<GitRepository, List<VirtualFile>> map = sortFilesByRepository(project, virtualFiles, ignoreNonGit);

    Map<VirtualFile, List<VirtualFile>> result = new HashMap<>();
    map.forEach((repo, files) -> result.put(repo.getRoot(), files));
    return result;
  }

  /**
   * @throws VcsException if non git files are passed
   */
  @RequiresBackgroundThread
  public static @NotNull Map<GitRepository, List<VirtualFile>> sortFilesByRepository(@NotNull Project project,
                                                                                     @NotNull Collection<? extends VirtualFile> filePaths)
    throws VcsException {
    return sortFilesByRepository(project, filePaths, false);
  }

  @RequiresBackgroundThread
  public static @NotNull Map<GitRepository, List<VirtualFile>> sortFilesByRepositoryIgnoringMissing(@NotNull Project project,
                                                                                                    @NotNull Collection<? extends VirtualFile> virtualFiles) {
    try {
      return sortFilesByRepository(project, virtualFiles, true);
    }
    catch (VcsException e) {
      LOG.error(new IllegalArgumentException(e));
      return Collections.emptyMap();
    }
  }

  @RequiresBackgroundThread
  private static @NotNull Map<GitRepository, List<VirtualFile>> sortFilesByRepository(@NotNull Project project,
                                                                                      @NotNull Collection<? extends VirtualFile> virtualFiles,
                                                                                      boolean ignoreNonGit)
    throws VcsException {
    GitRepositoryManager manager = GitRepositoryManager.getInstance(project);

    Map<GitRepository, List<VirtualFile>> result = new HashMap<>();
    for (VirtualFile file : virtualFiles) {
      // directory is reported only when it is a submodule or a mistakenly non-ignored nested root
      // => it should be treated in the context of super-root
      VirtualFile actualFile = file.isDirectory() ? file.getParent() : file;

      GitRepository repository = manager.getRepositoryForFile(actualFile);
      if (repository == null) {
        if (ignoreNonGit) continue;
        throw new GitRepositoryNotFoundException(file);
      }

      List<VirtualFile> files = result.computeIfAbsent(repository, key -> new ArrayList<>());
      files.add(file);
    }
    return result;
  }

  private static @NotNull Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Project project,
                                                                                  @NotNull Collection<? extends FilePath> filePaths,
                                                                                  boolean ignoreNonGit)
    throws VcsException {
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    GitVcs gitVcs = GitVcs.getInstance(project);
    Map<VirtualFile, List<FilePath>> result = new HashMap<>();
    for (FilePath path : filePaths) {
      VcsRoot vcsRoot = manager.getVcsRootObjectFor(path);
      AbstractVcs vcs = vcsRoot != null ? vcsRoot.getVcs() : null;
      if (vcs == null || !vcs.equals(gitVcs)) {
        if (ignoreNonGit) continue;
        throw new GitRepositoryNotFoundException(path);
      }

      List<FilePath> paths = result.computeIfAbsent(vcsRoot.getPath(), key -> new ArrayList<>());
      paths.add(path);
    }
    return result;
  }

  /**
   * Parse UNIX timestamp as it is returned by the git
   *
   * @param value a value to parse
   * @return timestamp as {@link Date} object
   */
  public static Date parseTimestamp(@NonNls String value) {
    final long parsed;
    parsed = Long.parseLong(value.trim());
    return new Date(parsed * 1000);
  }

  /**
   * Parse UNIX timestamp returned from Git and handle {@link NumberFormatException} if one happens: return new {@link Date} and
   * log the error properly.
   * In some cases git output gets corrupted and this method is intended to catch the reason, why.
   *
   * @param value     Value to parse.
   * @param handler   Git handler that was called to received the output.
   * @param gitOutput Git output.
   * @return Parsed Date or {@code new Date} in the case of error.
   */
  public static Date parseTimestampWithNFEReport(@NonNls String value, GitHandler handler, String gitOutput) {
    try {
      return parseTimestamp(value);
    }
    catch (NumberFormatException e) {
      LOG.error("annotate(). NFE. Handler: " + handler + ". Output: " + gitOutput, e);
      return new Date();
    }
  }

  /**
   * Return a git root for the file path, by walking the FS up.
   * Uses nio to access the file system.
   * <p>
   * See {@link #isGitRoot(Path)} for obsolete reason.
   *
   * @return git root (folder containing the '.git') or null
   */
  @ApiStatus.Obsolete
  public static @Nullable VirtualFile findGitRootFor(@NotNull Path path) {
    try {
      Path root = path;
      while (root != null) {
        if (isGitRoot(root)) {
          return LocalFileSystem.getInstance().findFileByNioFile(root);
        }
        root = root.getParent();
      }
      return null;
    }
    catch (InvalidPathException e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }

  /**
   * @deprecated Prefer using {@link #isGitRoot(Path)} instead.
   */
  @Deprecated
  public static boolean isGitRoot(@NotNull File folder) {
    try {
      return isGitRoot(folder.toPath());
    }
    catch (InvalidPathException e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }

  /**
   * Check if the file is under git, by walking the FS up.
   * <p>
   * See {@link #isGitRoot(Path)} for obsolete reason.
   */
  @ApiStatus.Obsolete
  public static boolean isUnderGit(@NotNull VirtualFile vFile) {
    try {
      return findGitRootFor(vFile.toNioPath()) != null;
    }
    catch (InvalidPathException e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }

  /**
   * Check if the file is under git, by walking the FS up.
   * <p>
   * See {@link #isGitRoot(Path)} for obsolete reason.
   */
  @ApiStatus.Obsolete
  public static boolean isUnderGit(@NotNull FilePath path) {
    try {
      return findGitRootFor(Paths.get(path.getPath())) != null;
    }
    catch (InvalidPathException e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }


  /**
   * Return committer name based on author name and committer name
   *
   * @param authorName    the name of author
   * @param committerName the name of committer
   * @return just a name if they are equal, or name that includes both author and committer
   */
  public static @NlsSafe String adjustAuthorName(@NlsSafe String authorName, @NlsSafe String committerName) {
    if (!authorName.equals(committerName)) {
      committerName = GitBundle.message("commit.author.with.committer", authorName, committerName);
    }
    return committerName;
  }

  @RequiresBackgroundThread
  public static @NotNull Set<GitRepository> getRepositoriesForFiles(@NotNull Project project,
                                                                    @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    Set<GitRepository> result = new HashSet<>();
    for (VirtualFile file : files) {
      result.add(getRepositoryForFile(project, file));
    }
    return result;
  }

  /**
   * Get git time (UNIX time) basing on the date object
   *
   * @param time the time to convert
   * @return the time in git format
   */
  public static @NonNls String gitTime(Date time) {
    long t = time.getTime() / 1000;
    return Long.toString(t);
  }

  /**
   * Format revision number from long to 16-digit abbreviated revision
   *
   * @param rev the abbreviated revision number as long
   * @return the revision string
   */
  public static @NonNls String formatLongRev(long rev) {
    return String.format("%015x%x", (rev >>> 4), rev & 0xF);
  }

  public static void getLocalCommittedChanges(final Project project,
                                              final VirtualFile root,
                                              final Consumer<? super GitHandler> parametersSpecifier,
                                              final Consumer<? super GitCommittedChangeList> consumer,
                                              boolean skipDiffsForMerge) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setSilent(true);
    h.addParameters("--pretty=format:%x04%x01" + GitChangeUtils.COMMITTED_CHANGELIST_FORMAT, "--name-status");
    parametersSpecifier.consume(h);

    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    LOG.debug("getLocalCommittedChanges output: '" + output + "'");
    StringScanner s = new StringScanner(output);
    final StringBuilder sb = new StringBuilder();
    boolean firstStep = true;
    while (s.hasMoreData()) {
      final String line = s.line();
      final boolean lineIsAStart = line.startsWith("\u0004\u0001");
      if ((!firstStep) && lineIsAStart) {
        final StringScanner innerScanner = new StringScanner(sb.toString());
        sb.setLength(0);
        consumer.consume(GitChangeUtils.parseChangeList(project, root, innerScanner, skipDiffsForMerge, h, false, false));
      }
      sb.append(lineIsAStart ? line.substring(2) : line).append('\n');
      firstStep = false;
    }
    if (!sb.isEmpty()) {
      final StringScanner innerScanner = new StringScanner(sb.toString());
      sb.setLength(0);
      consumer.consume(GitChangeUtils.parseChangeList(project, root, innerScanner, skipDiffsForMerge, h, false, false));
    }
    if (s.hasMoreData()) {
      throw new IllegalStateException("More input is available: " + s.line());
    }
  }

  public static List<GitCommittedChangeList> getLocalCommittedChanges(final Project project,
                                                                      final VirtualFile root,
                                                                      final Consumer<? super GitHandler> parametersSpecifier)
    throws VcsException {
    final List<GitCommittedChangeList> rc = new ArrayList<>();

    getLocalCommittedChanges(project, root, parametersSpecifier, committedChangeList -> rc.add(committedChangeList), false);

    return rc;
  }

  /**
   * @throws VcsException if the path is invalid
   * @see VcsFileUtil#unescapeGitPath(String, String)
   */
  public static @NotNull String unescapePath(@NotNull @NonNls String path) throws VcsException {
    try {
      return VcsFileUtil.unescapeGitPath(path);
    }
    catch (IllegalStateException e) {
      throw new VcsException(e);
    }
  }

  public static boolean justOneGitRepository(Project project) {
    if (project.isDisposed()) {
      return true;
    }
    GitRepositoryManager manager = getRepositoryManager(project);
    return !manager.moreThanOneRoot();
  }


  public static @Nullable GitRemote findRemoteByName(@NotNull GitRepository repository, @NotNull @NonNls String name) {
    return findRemoteByName(repository.getRemotes(), name);
  }

  public static @Nullable GitRemote findRemoteByName(Collection<GitRemote> remotes, @NotNull @NonNls String name) {
    return ContainerUtil.find(remotes, remote -> remote.getName().equals(name));
  }

  public static @Nullable GitRemoteBranch findRemoteBranch(@NotNull GitRepository repository,
                                                           final @NotNull GitRemote remote,
                                                           @NotNull @NonNls String nameAtRemote) {
    return ContainerUtil.find(repository.getBranches().getRemoteBranches(), remoteBranch -> {
      return remoteBranch.getRemote().equals(remote) &&
             remoteBranch.getNameForRemoteOperations().equals(GitBranchUtil.stripRefsPrefix(nameAtRemote));
    });
  }

  public static @NotNull GitRemoteBranch findOrCreateRemoteBranch(@NotNull GitRepository repository,
                                                                  @NotNull GitRemote remote,
                                                                  @NotNull @NonNls String branchName) {
    GitRemoteBranch remoteBranch = findRemoteBranch(repository, remote, branchName);
    return ObjectUtils.notNull(remoteBranch, new GitStandardRemoteBranch(remote, branchName));
  }

  /**
   * @param remotes is REQUIRED to parse 'origin/feature/branch' references:
   *                these can be both 'branch on origin/feature remote' and 'feature/branch on origin remote'.
   */
  public static @NotNull GitRemoteBranch parseRemoteBranch(@NotNull String fullBranchName,
                                                           @NotNull Collection<GitRemote> remotes) {
    String stdName = GitBranchUtil.stripRefsPrefix(fullBranchName);

    int slash = stdName.indexOf('/');
    if (slash == -1) { // .git/refs/remotes/my_branch => git-svn
      return new GitSvnRemoteBranch(fullBranchName);
    }
    else {
      GitRemote remote;
      String remoteName;
      String branchName;
      do {
        remoteName = stdName.substring(0, slash);
        branchName = stdName.substring(slash + 1);
        remote = findRemoteByName(remotes, remoteName);
        slash = stdName.indexOf('/', slash + 1);
      }
      while (remote == null && slash >= 0);

      if (remote == null) {
        // user may remove the remote section from .git/config, but leave remote refs untouched in .git/refs/remotes
        // assume that remote names with slashes are less common than branches
        int firstSlash = stdName.indexOf('/');
        remoteName = stdName.substring(0, firstSlash);
        branchName = stdName.substring(firstSlash + 1);

        LOG.trace(String.format("No remote found with the name [%s]. All remotes: %s", remoteName, remotes));
        GitRemote fakeRemote = new GitRemote(remoteName, emptyList(), emptyList(), emptyList(), emptyList());
        return new GitStandardRemoteBranch(fakeRemote, branchName);
      }
      return new GitStandardRemoteBranch(remote, branchName);
    }
  }

  public static @Unmodifiable @NotNull Collection<VirtualFile> getRootsFromRepositories(@NotNull @Unmodifiable Collection<? extends GitRepository> repositories) {
    return ContainerUtil.map(repositories, Repository::getRoot);
  }

  @RequiresBackgroundThread
  public static @NotNull Collection<GitRepository> getRepositoriesFromRoots(@NotNull GitRepositoryManager repositoryManager,
                                                                            @NotNull Collection<? extends VirtualFile> roots) {
    Collection<GitRepository> repositories = new ArrayList<>(roots.size());
    for (VirtualFile root : roots) {
      GitRepository repo = repositoryManager.getRepositoryForRoot(root);
      if (repo == null) {
        LOG.error("Repository not found for root " + root);
      }
      else {
        repositories.add(repo);
      }
    }
    return repositories;
  }

  /**
   * Returns absolute paths which have changed remotely comparing to the current branch, i.e. performs
   * {@code git diff --name-only master..origin/master}
   * <p/>
   * Paths are absolute, Git-formatted (i.e. with forward slashes).
   */
  public static @NotNull Collection<String> getPathsDiffBetweenRefs(@NotNull Git git, @NotNull GitRepository repository,
                                                                    @NotNull @NonNls String beforeRef, @NotNull @NonNls String afterRef)
    throws VcsException {
    List<String> parameters = Arrays.asList("--name-only", "--pretty=format:");
    String range = beforeRef + ".." + afterRef;
    GitCommandResult result = git.diff(repository, parameters, range);
    if (!result.success()) {
      LOG.info(String.format("Couldn't get diff in range [%s] for repository [%s]", range, repository.toLogString()));
      return Collections.emptyList();
    }

    final Collection<String> remoteChanges = new HashSet<>();
    for (StringScanner s = new StringScanner(result.getOutputAsJoinedString()); s.hasMoreData(); ) {
      final String relative = s.line();
      if (StringUtil.isEmptyOrSpaces(relative)) {
        continue;
      }
      final String path = repository.getRoot().getPath() + "/" + unescapePath(relative);
      remoteChanges.add(path);
    }
    return remoteChanges;
  }

  public static @NotNull GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return GitRepositoryManager.getInstance(project);
  }

  @RequiresBackgroundThread
  public static @NotNull GitRepository getRepositoryForFile(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @RequiresBackgroundThread
  public static @NotNull GitRepository getRepositoryForFile(@NotNull Project project, @NotNull FilePath file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @RequiresBackgroundThread
  public static @NotNull GitRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository == null) throw new GitRepositoryNotFoundException(root);
    return repository;
  }

  public static @Nullable GitRepository getRepositoryForRootOrLogError(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository == null) LOG.error(new GitRepositoryNotFoundException(root));
    return repository;
  }

  public static @NotNull VirtualFile getRootForFile(@NotNull Project project, @NotNull FilePath filePath) throws VcsException {
    VcsRoot root = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(filePath);
    if (isGitVcsRoot(root)) return root.getPath();

    Repository repository = VcsRepositoryManager.getInstance(project).getExternalRepositoryForFile(filePath);
    if (repository instanceof GitRepository) return repository.getRoot();
    throw new GitRepositoryNotFoundException(filePath);
  }

  public static @NotNull VirtualFile getRootForFile(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
    VcsRoot root = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file);
    if (isGitVcsRoot(root)) return root.getPath();

    Repository repository = VcsRepositoryManager.getInstance(project).getExternalRepositoryForFile(file);
    if (repository instanceof GitRepository) return repository.getRoot();
    throw new GitRepositoryNotFoundException(file);
  }

  private static boolean isGitVcsRoot(@Nullable VcsRoot root) {
    if (root == null) return false;
    AbstractVcs vcs = root.getVcs();
    if (vcs == null) return false;
    return GitVcs.getKey().equals(vcs.getKeyInstanceMethod());
  }

  /**
   * Show changes made in the specified revision.
   *
   * @param project    the project
   * @param revision   the revision number
   * @param file       the file affected by the revision
   * @param local      pass true to let the diff be editable, i.e. making the revision "at the right" be a local (current) revision.
   *                   pass false to let both sides of the diff be non-editable.
   * @param revertable pass true to let "Revert" action be active.
   */
  public static void showSubmittedFiles(final Project project, @NonNls String revision, final VirtualFile file,
                                        final boolean local, final boolean revertable) {
    new Task.Backgroundable(project, GitBundle.message("changes.retrieving", revision)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          VirtualFile vcsRoot = getRootForFile(project, file);
          final CommittedChangeList changeList = GitChangeUtils.getRevisionChanges(project, vcsRoot, revision, true, local, revertable);
          UIUtil.invokeLaterIfNeeded(
            () -> AbstractVcsHelper.getInstance(project)
              .showChangesListBrowser(changeList, GitBundle.message("paths.affected.title", revision)));
        }
        catch (final VcsException e) {
          UIUtil.invokeLaterIfNeeded(() -> GitUIUtil.showOperationError(project, e, GitBundle.message("operation.name.loading.revision")));
        }
      }
    }.queue();
  }


  /**
   * Returns the tracking information (remote and the name of the remote branch), or null if we are not on a branch.
   */
  public static @Nullable GitBranchTrackInfo getTrackInfoForCurrentBranch(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      return null;
    }
    return GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
  }

  /**
   * git diff --name-only [--cached]
   *
   * @param staged if true checks the staging area, if false checks unstaged files.
   * @return true if there is anything in the unstaged/staging area, false if the unstaged/staging area is empty.
   */
  public static boolean hasLocalChanges(boolean staged, Project project, VirtualFile root) throws VcsException {
    GitLineHandler diff = new GitLineHandler(project, root, GitCommand.DIFF);
    diff.addParameters("--name-only");
    diff.addParameters("--no-renames");
    if (staged) {
      diff.addParameters("--cached");
    }
    diff.setStdoutSuppressed(true);
    diff.setStderrSuppressed(true);
    diff.setSilent(true);
    final String output = Git.getInstance().runCommand(diff).getOutputOrThrow();
    return !output.trim().isEmpty();
  }

  public static @Nullable VirtualFile findRefreshFileOrLog(@NotNull @NonNls String absolutePath) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absolutePath);
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath);
    }
    if (file == null) {
      LOG.debug("VirtualFile not found for " + absolutePath);
    }
    return file;
  }

  public static @NotNull String toAbsolute(@NotNull VirtualFile root, @NotNull @NonNls String relativePath) {
    return StringUtil.trimEnd(root.getPath(), "/") + "/" + StringUtil.trimStart(relativePath, "/");
  }

  public static @NotNull Collection<String> toAbsolute(final @NotNull VirtualFile root, @NotNull Collection<@NonNls String> relativePaths) {
    return ContainerUtil.map(relativePaths, s -> toAbsolute(root, s));
  }

  /**
   * Given the list of paths converts them to the list of {@link Change Changes} found in the {@link ChangeListManager},
   * i.e. this works only for local changes. </br>
   * Paths can be absolute or relative to the repository.
   * If a path is not found in the local changes, it is ignored, but the fact is logged.
   */
  public static @NotNull List<Change> findLocalChangesForPaths(@NotNull Project project, @NotNull VirtualFile root,
                                                               @NotNull Collection<@NonNls String> affectedPaths, boolean relativePaths) {
    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
    List<Change> affectedChanges = new ArrayList<>();
    for (String path : affectedPaths) {
      String absolutePath = relativePaths ? toAbsolute(root, path) : path;
      VirtualFile file = findRefreshFileOrLog(absolutePath);
      if (file != null) {
        Change change = changeListManager.getChange(file);
        if (change != null) {
          affectedChanges.add(change);
        }
        else {
          String message = "Change is not found for " + file.getPath();
          if (changeListManager.isInUpdate()) {
            message += " because ChangeListManager is being updated.";
          }
          LOG.debug(message);
        }
      }
    }
    return affectedChanges;
  }

  public static void showPathsInDialog(@NotNull Project project,
                                       @NotNull Collection<@NonNls String> absolutePaths,
                                       @NotNull @NlsContexts.DialogTitle String title,
                                       @Nullable @NlsContexts.DialogMessage String description) {
    showPathsInDialog(project, absolutePaths, title, description, null);
  }

  public static void showPathsInDialog(@NotNull Project project,
                                       @NotNull Collection<@NonNls String> absolutePaths,
                                       @NotNull @NlsContexts.DialogTitle String title,
                                       @Nullable @NlsContexts.DialogMessage String description,
                                       @Nullable @NlsContexts.StatusText String emptyText) {
    DialogBuilder builder = new DialogBuilder(project);
    GitSimplePathsBrowser browser = new GitSimplePathsBrowser(project, absolutePaths);
    if (emptyText != null) {
      browser.setEmptyText(emptyText);
    }
    builder.setCenterPanel(browser);
    if (description != null) {
      builder.setNorthPanel(new MultiLineLabel(description));
    }
    builder.addOkAction();
    builder.setTitle(title);
    builder.show();
  }

  public static @NlsSafe @NotNull String cleanupErrorPrefixes(@NotNull @NlsSafe String msg) {
    final @NonNls String[] PREFIXES = {"fatal:", "error:"};
    msg = msg.trim();
    for (String prefix : PREFIXES) {
      if (msg.startsWith(prefix)) {
        msg = msg.substring(prefix.length()).trim();
      }
    }
    return msg;
  }

  public static @Nullable GitRemote getDefaultRemote(@NotNull Collection<GitRemote> remotes) {
    return ContainerUtil.find(remotes, r -> r.getName().equals(GitRemote.ORIGIN));
  }

  public static @Nullable GitRemote getDefaultOrFirstRemote(@NotNull Collection<GitRemote> remotes) {
    GitRemote result = getDefaultRemote(remotes);
    return result == null ? ContainerUtil.getFirstItem(remotes) : result;
  }

  public static @NotNull String joinToHtml(@NotNull Collection<? extends GitRepository> repositories) {
    return StringUtil.join(repositories, repository -> repository.getPresentableUrl(), UIUtil.BR);
  }

  public static @Nls @NotNull String mention(@NotNull GitRepository repository) {
    return getRepositoryManager(repository.getProject()).moreThanOneRoot()
           ? GitBundle.message("mention.in", getShortRepositoryName(repository))
           : "";
  }

  public static @Nls @NotNull String mention(@NotNull Collection<? extends GitRepository> repositories) {
    if (repositories.isEmpty()) return "";
    return GitBundle.message("mention.in", joinShortNames(repositories, -1));
  }

  public static void updateRepositories(@NotNull Collection<? extends GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }

  public static boolean hasGitRepositories(@NotNull Project project) {
    return !getRepositories(project).isEmpty();
  }

  public static @NotNull Collection<GitRepository> getRepositories(@NotNull Project project) {
    return getRepositoryManager(project).getRepositories();
  }

  public static @NotNull Collection<GitRepository> getRepositoriesInStates(@NotNull Project project, Repository.State @NotNull ... states) {
    Set<Repository.State> stateSet = ContainerUtil.newHashSet(states);
    return ContainerUtil.filter(getRepositories(project), repository -> stateSet.contains(repository.getState()));
  }

  /**
   * Checks if the given paths are equal only by case.
   * It is expected that the paths are different at least by the case.
   */
  public static boolean isCaseOnlyChange(@NotNull @NonNls String oldPath, @NotNull @NonNls String newPath) {
    if (oldPath.equalsIgnoreCase(newPath)) {
      if (oldPath.equals(newPath)) {
        LOG.info("Comparing perfectly equal paths: " + newPath);
      }
      return true;
    }
    return false;
  }

  public static @NonNls @NotNull String getLogStringGitDiffChanges(@NotNull @NonNls String root,
                                                                   @NotNull Collection<? extends GitChangeUtils.GitDiffChange> changes) {
    return getLogString(root, changes, it -> it.getBeforePath(), it -> it.getAfterPath());
  }

  public static @NonNls @NotNull String getLogString(@NotNull @NonNls String root, @NotNull Collection<? extends Change> changes) {
    return getLogString(root, changes, ChangesUtil::getBeforePath, ChangesUtil::getAfterPath);
  }

  public static @NonNls @NotNull <T> String getLogString(@NotNull @NonNls String root, @NotNull Collection<? extends T> changes,
                                                         @NotNull Convertor<? super T, ? extends FilePath> beforePathGetter,
                                                         @NotNull Convertor<? super T, ? extends FilePath> afterPathGetter) {
    return StringUtil.join(changes, change -> {
      FilePath after = afterPathGetter.convert(change);
      FilePath before = beforePathGetter.convert(change);
      if (before == null) {
        return "A: " + getRelativePath(root, after);
      }
      else if (after == null) {
        return "D: " + getRelativePath(root, before);
      }
      else if (ChangesUtil.equalsCaseSensitive(before, after)) {
        return "M: " + getRelativePath(root, after);
      }
      else {
        return "R: " + getRelativePath(root, before) + " -> " + getRelativePath(root, after);
      }
    }, ", ");
  }

  public static @Nullable String getRelativePath(@NotNull String root, @NotNull FilePath after) {
    return FileUtil.getRelativePath(root, after.getPath(), File.separatorChar);
  }

  /**
   * <p>Finds the local changes which are "the same" as the given changes.</p>
   * <p>The purpose of this method is to get actual local changes after some other changes were applied to the working tree
   * (e.g. if they were cherry-picked from a commit). Working with the original non-local changes is limited, in particular,
   * the difference between content revisions may be not the same as the local change.</p>
   * <p>"The same" here means the changes made in the same files. It is possible that there was a change made in file A in the original
   * commit, but there are no local changes made in file A. Such situations are ignored.</p>
   */
  public static @NotNull Collection<Change> findCorrespondentLocalChanges(@NotNull ChangeListManager changeListManager,
                                                                          @NotNull Collection<? extends Change> originalChanges) {
    ObjectOpenHashSet<Change> allChanges = new ObjectOpenHashSet<>(changeListManager.getAllChanges());
    return ContainerUtil.mapNotNull(originalChanges, allChanges::get);
  }

  /**
   * A convenience method to refresh either a part of the VFS modified by the given changes, or the whole root recursively.
   *
   * @param changes The changes which files were modified by a Git operation.
   *                If null, the whole root is refreshed. Otherwise, only the files touched by these changes.
   */
  public static void refreshVfs(@NotNull VirtualFile root, @Nullable Collection<? extends Change> changes) {
    if (changes == null || Registry.is("git.refresh.vfs.total")) {
      refreshVfsInRoot(root);
    }
    else {
      RefreshVFsSynchronously.updateChanges(changes);

      // the file opened in the editor may accidentally capture an intermediate state for back-and-forth changes during rebase
      // these may not be refreshed if 'before rebase' and 'after rebase' states match for the file
      SaveAndSyncHandler.getInstance().refreshOpenFiles();
    }
  }

  public static void refreshVfsInRoot(@NotNull VirtualFile root) {
    refreshVfsInRoots(Collections.singleton(root));
  }

  public static void refreshVfsInRoots(@NotNull Collection<VirtualFile> roots) {
    RefreshVFsSynchronously.trace("refresh roots " + roots);
    RefreshVFsSynchronously.refreshVirtualFilesRecursive(roots);
  }

  public static void updateAndRefreshChangedVfs(@NotNull GitRepository repository, @Nullable Hash startHash) {
    repository.update();
    refreshChangedVfs(repository, startHash);
  }

  public static void refreshChangedVfs(@NotNull GitRepository repository, @Nullable Hash startHash) {
    Collection<Change> changes = null;
    if (startHash != null) {
      Hash currentHash = getHead(repository);
      if (currentHash != null) {
        RefreshVFsSynchronously.trace(String.format("changes: %s -> %s", startHash.asString(), currentHash.asString()));
        changes = GitChangeUtils.getDiff(repository, startHash.asString(), currentHash.asString(), false);
      }
    }
    refreshVfs(repository.getRoot(), changes);
  }

  /**
   * Check if the directory is a valid git root, by parsing the .git file/directory.
   * <p>
   * Typically, IDE should use its configured VCS directories, via {@link ProjectLevelVcsManager#getVcsFor(FilePath)} or
   * {@link GitRepositoryManager#getRepositoryForFile(VirtualFile)}.
   * While there are exist valid usages of this method, it is marked as Obsolete to make sure it is used only when actually needed.
   * <p>
   * If it's used to detect new VCS mappings, consider checking {@link VcsUtil#shouldDetectVcsMappingsFor(Project)} first.
   *
   * @see #findGitDir(VirtualFile)
   */
  @ApiStatus.Obsolete
  public static boolean isGitRoot(@NotNull Path rootDir) {
    return findGitDir(rootDir) != null;
  }

  /**
   * Check if the given root is a valid git root and return gitDir location
   * For worktrees - location of the 'main_repo/.git/worktrees/worktree_name/' folder.
   * <p>
   * See {@link #isGitRoot(Path)} for obsolete reason.
   */
  @ApiStatus.Obsolete
  public static @Nullable Path findGitDir(@NotNull Path rootDir) {
    Path dotGit = rootDir.resolve(DOT_GIT);
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(dotGit, BasicFileAttributes.class);
    }
    catch (IOException ignore) {
      return null;
    }

    if (attributes.isDirectory()) {
      try {
        BasicFileAttributes headExists = Files.readAttributes(dotGit.resolve(HEAD_FILE), BasicFileAttributes.class);
        if (headExists.isRegularFile()) {
          return dotGit;
        }
        return null;
      }
      catch (IOException ignore) {
        return null;
      }
    }
    if (!attributes.isRegularFile()) {
      return null;
    }

    String content;
    try {
      content = DvcsUtil.tryOrThrow(() -> StringUtil.convertLineSeparators(Files.readString(dotGit)).trim(), dotGit);
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return null;
    }

    String pathToDir = parsePathToRepository(content);
    if (pathToDir == null) return null;

    return findRealRepositoryDir(rootDir, pathToDir);
  }

  public static void generateGitignoreFileIfNeeded(@NotNull Project project, @NotNull VirtualFile ignoreFileRoot) {
    VcsImplUtil.generateIgnoreFileIfNeeded(project, GitVcs.getInstance(project), ignoreFileRoot);
  }

  public static <T extends Throwable> void tryRunOrClose(@NotNull AutoCloseable closeable,
                                                         @NotNull ThrowableRunnable<T> runnable) throws T {
    try {
      runnable.run();
    }
    catch (Throwable e) {
      try {
        closeable.close();
      }
      catch (Throwable e2) {
        e.addSuppressed(e2);
      }
      throw e;
    }
  }

  private static final class GitRepositoryNotFoundException extends VcsException {

    private GitRepositoryNotFoundException(@NotNull VirtualFile file) {
      super(GitBundle.message("repository.not.found.error", file.getPresentableUrl()));
    }

    private GitRepositoryNotFoundException(@NotNull FilePath filePath) {
      super(GitBundle.message("repository.not.found.error", filePath.getPresentableUrl()));
    }
  }

  public static @NotNull <T extends GitHandler> T createHandlerWithPaths(@Nullable Collection<? extends FilePath> paths,
                                                                         @NotNull Computable<T> handlerBuilder) {
    T handler = handlerBuilder.compute();
    handler.endOptions();
    if (paths != null) {
      handler.addRelativePaths(paths);
      if (handler.isLargeCommandLine()) {
        handler = handlerBuilder.compute();
        handler.endOptions();
      }
    }
    return handler;
  }

  public static @Nullable Hash getHead(@NotNull GitRepository repository) {
    GitCommandResult result = Git.getInstance().tip(repository, HEAD);
    if (!result.success()) {
      LOG.warn("Couldn't identify the HEAD for " + repository + ": " + result.getErrorOutputAsJoinedString());
      return null;
    }
    String head = result.getOutputAsJoinedString();
    return HashImpl.build(head);
  }

  public static boolean isHashString(@NotNull @NonNls String revision) {
    return isHashString(revision, true);
  }

  public static boolean isHashString(@NotNull @NonNls String revision, boolean fullHashOnly) {
    if (fullHashOnly) {
      return HASH_STRING_PATTERN.matcher(revision).matches();
    }
    else {
      return VcsLogUtil.HASH_REGEX.matcher(revision).matches();
    }
  }
}
