// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
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
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitCommittedChangeList;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitSimplePathsBrowser;
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.openapi.vcs.changes.ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY;

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
  private final static Logger LOG = Logger.getInstance(GitUtil.class);
  private static final @NonNls String HEAD_FILE = "HEAD";

  /**
   * A private constructor to suppress instance creation
   */
  private GitUtil() {
    // do nothing
  }

  /**
   * Returns the Git repository location for the given repository root directory, or null if nothing can be found.
   * Able to find the real repository root of a submodule or of a working tree.
   * <p/>
   * More precisely: checks if there is {@code .git} directory or file directly under rootDir. <br/>
   * If there is a directory, performs a quick check that it looks like a Git repository;<br/>
   * if it is a file, follows the path written inside this file to find the actual repo dir.
   */
  @Nullable
  public static VirtualFile findGitDir(@NotNull VirtualFile rootDir) {
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
    File file = findRealRepositoryDir(rootDir.toNioPath(), pathToDir);
    if (file == null) return null;
    return VcsUtil.getVirtualFileWithRefresh(file);
  }

  @Nullable
  private static File findRealRepositoryDir(@NotNull @NonNls Path rootPath, @NotNull @NonNls String path) {
    if (!FileUtil.isAbsolute(path)) {
      String canonicalPath = FileUtil.toCanonicalPath(FileUtil.join(rootPath.toString(), path), true);
      path = FileUtil.toSystemIndependentName(canonicalPath);
    }
    File file = new File(path);
    return file.isDirectory() ? file : null;
  }

  @Nullable
  private static String parsePathToRepository(@NotNull @NonNls String content) {
    content = content.trim();
    if (content.startsWith(REPO_PATH_LINK_PREFIX)) {
      content = content.substring(REPO_PATH_LINK_PREFIX.length()).trim();
    }
    if (content.isEmpty() || content.contains("\n")) return null;
    return content;
  }

  @Nullable
  private static String readContent(@NotNull VirtualFile dotGit) {
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
  @NotNull
  private static String readFile(@NotNull VirtualFile file) throws IOException {
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
  @NotNull
  @RequiresBackgroundThread
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Project project,
                                                                       @NotNull Collection<? extends VirtualFile> virtualFiles)
    throws VcsException {
    return sortFilesByGitRoot(project, virtualFiles, false);
  }

  @NotNull
  @RequiresBackgroundThread
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRootIgnoringMissing(@NotNull Project project,
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
  @NotNull
  @RequiresBackgroundThread
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Project project,
                                                                        @NotNull Collection<? extends FilePath> filePaths)
    throws VcsException {
    return sortFilePathsByGitRoot(project, filePaths, false);
  }

  @NotNull
  @RequiresBackgroundThread
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRootIgnoringMissing(@NotNull Project project,
                                                                                       @NotNull Collection<? extends FilePath> filePaths) {
    try {
      return sortFilePathsByGitRoot(project, filePaths, true);
    }
    catch (VcsException e) {
      LOG.error(new IllegalArgumentException(e));
      return Collections.emptyMap();
    }
  }

  @NotNull
  @RequiresBackgroundThread
  private static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Project project,
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
  @NotNull
  @RequiresBackgroundThread
  public static Map<GitRepository, List<VirtualFile>> sortFilesByRepository(@NotNull Project project,
                                                                            @NotNull Collection<? extends VirtualFile> filePaths)
    throws VcsException {
    return sortFilesByRepository(project, filePaths, false);
  }

  @NotNull
  @RequiresBackgroundThread
  public static Map<GitRepository, List<VirtualFile>> sortFilesByRepositoryIgnoringMissing(@NotNull Project project,
                                                                                           @NotNull Collection<? extends VirtualFile> virtualFiles) {
    try {
      return sortFilesByRepository(project, virtualFiles, true);
    }
    catch (VcsException e) {
      LOG.error(new IllegalArgumentException(e));
      return Collections.emptyMap();
    }
  }

  @NotNull
  @RequiresBackgroundThread
  private static Map<GitRepository, List<VirtualFile>> sortFilesByRepository(@NotNull Project project,
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

  @NotNull
  private static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Project project,
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
   * Return a git root for the file path (the parent directory with ".git" subdirectory).
   * Uses nio to access the file system.
   *
   * @return git root for the file or null if the file is not under git
   * @see GitRepositoryManager#getRepositoryForFile(FilePath)
   */
  @Nullable
  public static VirtualFile findGitRootFor(@NotNull Path path) {
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
   * Check if the virtual file under git
   */
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
   * Check if the file path is under git
   */
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
  @NlsSafe
  public static String adjustAuthorName(@NlsSafe String authorName, @NlsSafe String committerName) {
    if (!authorName.equals(committerName)) {
      committerName = GitBundle.message("commit.author.with.committer", authorName, committerName);
    }
    return committerName;
  }

  @NotNull
  @RequiresBackgroundThread
  public static Set<GitRepository> getRepositoriesForFiles(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files)
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
  @NonNls
  public static String gitTime(Date time) {
    long t = time.getTime() / 1000;
    return Long.toString(t);
  }

  /**
   * Format revision number from long to 16-digit abbreviated revision
   *
   * @param rev the abbreviated revision number as long
   * @return the revision string
   */
  @NonNls
  public static String formatLongRev(long rev) {
    return String.format("%015x%x", (rev >>> 4), rev & 0xF);
  }

  public static void getLocalCommittedChanges(final Project project,
                                              final VirtualFile root,
                                              final Consumer<? super GitHandler> parametersSpecifier,
                                              final Consumer<? super GitCommittedChangeList> consumer, boolean skipDiffsForMerge) throws VcsException {
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
    if (sb.length() > 0) {
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
  @NotNull
  public static String unescapePath(@NotNull @NonNls String path) throws VcsException {
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


  @Nullable
  public static GitRemote findRemoteByName(@NotNull GitRepository repository, @NotNull @NonNls String name) {
    return findRemoteByName(repository.getRemotes(), name);
  }

  @Nullable
  public static GitRemote findRemoteByName(Collection<GitRemote> remotes, @NotNull @NonNls String name) {
    return ContainerUtil.find(remotes, remote -> remote.getName().equals(name));
  }

  @Nullable
  public static GitRemoteBranch findRemoteBranch(@NotNull GitRepository repository,
                                                 @NotNull final GitRemote remote,
                                                 @NotNull @NonNls String nameAtRemote) {
    return ContainerUtil.find(repository.getBranches().getRemoteBranches(), remoteBranch -> {
      return remoteBranch.getRemote().equals(remote) &&
             remoteBranch.getNameForRemoteOperations().equals(GitBranchUtil.stripRefsPrefix(nameAtRemote));
    });
  }

  @NotNull
  public static GitRemoteBranch findOrCreateRemoteBranch(@NotNull GitRepository repository,
                                                         @NotNull GitRemote remote,
                                                         @NotNull @NonNls String branchName) {
    GitRemoteBranch remoteBranch = findRemoteBranch(repository, remote, branchName);
    return ObjectUtils.notNull(remoteBranch, new GitStandardRemoteBranch(remote, branchName));
  }

  @NotNull
  public static Collection<VirtualFile> getRootsFromRepositories(@NotNull Collection<? extends GitRepository> repositories) {
    return ContainerUtil.map(repositories, Repository::getRoot);
  }

  @NotNull
  @RequiresBackgroundThread
  public static Collection<GitRepository> getRepositoriesFromRoots(@NotNull GitRepositoryManager repositoryManager,
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
  @NotNull
  public static Collection<String> getPathsDiffBetweenRefs(@NotNull Git git, @NotNull GitRepository repository,
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

  @NotNull
  public static GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return GitRepositoryManager.getInstance(project);
  }

  @NotNull
  @RequiresBackgroundThread
  public static GitRepository getRepositoryForFile(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @NotNull
  @RequiresBackgroundThread
  public static GitRepository getRepositoryForFile(@NotNull Project project, @NotNull FilePath file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @NotNull
  @RequiresBackgroundThread
  public static GitRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository == null) throw new GitRepositoryNotFoundException(root);
    return repository;
  }

  @Nullable
  public static GitRepository getRepositoryForRootOrLogError(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository == null) LOG.error(new GitRepositoryNotFoundException(root));
    return repository;
  }

  @NotNull
  public static VirtualFile getRootForFile(@NotNull Project project, @NotNull FilePath filePath) throws VcsException {
    VcsRoot root = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(filePath);
    if (isGitVcsRoot(root)) return root.getPath();

    Repository repository = VcsRepositoryManager.getInstance(project).getExternalRepositoryForFile(filePath);
    if (repository instanceof GitRepository) return repository.getRoot();
    throw new GitRepositoryNotFoundException(filePath);
  }

  @NotNull
  public static VirtualFile getRootForFile(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
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
  @Nullable
  public static GitBranchTrackInfo getTrackInfoForCurrentBranch(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      return null;
    }
    return GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
  }

  /**
   * git diff --name-only [--cached]
   * @return true if there is anything in the unstaged/staging area, false if the unstaged/staging area is empty.
   * @param staged if true checks the staging area, if false checks unstaged files.
   * @param project
   * @param root
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

  @Nullable
  public static VirtualFile findRefreshFileOrLog(@NotNull @NonNls String absolutePath) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absolutePath);
    if (file == null) {
      file = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath);
    }
    if (file == null) {
      LOG.debug("VirtualFile not found for " + absolutePath);
    }
    return file;
  }

  @NotNull
  public static String toAbsolute(@NotNull VirtualFile root, @NotNull @NonNls String relativePath) {
    return StringUtil.trimEnd(root.getPath(), "/") + "/" + StringUtil.trimStart(relativePath, "/");
  }

  @NotNull
  public static Collection<String> toAbsolute(@NotNull final VirtualFile root, @NotNull Collection<@NonNls String> relativePaths) {
    return ContainerUtil.map(relativePaths, s -> toAbsolute(root, s));
  }

  /**
   * Given the list of paths converts them to the list of {@link Change Changes} found in the {@link ChangeListManager},
   * i.e. this works only for local changes. </br>
   * Paths can be absolute or relative to the repository.
   * If a path is not found in the local changes, it is ignored, but the fact is logged.
   */
  @NotNull
  public static List<Change> findLocalChangesForPaths(@NotNull Project project, @NotNull VirtualFile root,
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
    DialogBuilder builder = new DialogBuilder(project);
    builder.setCenterPanel(new GitSimplePathsBrowser(project, absolutePaths));
    if (description != null) {
      builder.setNorthPanel(new MultiLineLabel(description));
    }
    builder.addOkAction();
    builder.setTitle(title);
    builder.show();
  }

  @NlsSafe
  @NotNull
  public static String cleanupErrorPrefixes(@NotNull @NlsSafe String msg) {
    final @NonNls String[] PREFIXES = { "fatal:", "error:" };
    msg = msg.trim();
    for (String prefix : PREFIXES) {
      if (msg.startsWith(prefix)) {
        msg = msg.substring(prefix.length()).trim();
      }
    }
    return msg;
  }

  @Nullable
  public static GitRemote getDefaultRemote(@NotNull Collection<GitRemote> remotes) {
    return ContainerUtil.find(remotes, r -> r.getName().equals(GitRemote.ORIGIN));
  }

  @Nullable
  public static GitRemote getDefaultOrFirstRemote(@NotNull Collection<GitRemote> remotes) {
    GitRemote result = getDefaultRemote(remotes);
    return result == null ? ContainerUtil.getFirstItem(remotes) : result;
  }

  @NotNull
  public static String joinToHtml(@NotNull Collection<? extends GitRepository> repositories) {
    return StringUtil.join(repositories, repository -> repository.getPresentableUrl(), UIUtil.BR);
  }

  @Nls
  @NotNull
  public static String mention(@NotNull GitRepository repository) {
    return getRepositoryManager(repository.getProject()).moreThanOneRoot()
           ? GitBundle.message("mention.in", getShortRepositoryName(repository))
           : "";
  }

  @Nls
  @NotNull
  public static String mention(@NotNull Collection<? extends GitRepository> repositories) {
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

  @NotNull
  public static Collection<GitRepository> getRepositories(@NotNull Project project) {
    return getRepositoryManager(project).getRepositories();
  }

  @NotNull
  public static Collection<GitRepository> getRepositoriesInState(@NotNull Project project, @NotNull Repository.State state) {
    return ContainerUtil.filter(getRepositories(project), repository -> repository.getState() == state);
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

  @NonNls
  @NotNull
  public static String getLogStringGitDiffChanges(@NotNull @NonNls String root,
                                                  @NotNull Collection<? extends GitChangeUtils.GitDiffChange> changes) {
    return getLogString(root, changes, it -> it.getBeforePath(), it -> it.getAfterPath());
  }

  @NonNls
  @NotNull
  public static String getLogString(@NotNull @NonNls String root, @NotNull Collection<? extends Change> changes) {
    return getLogString(root, changes, ChangesUtil::getBeforePath, ChangesUtil::getAfterPath);
  }

  @NonNls
  @NotNull
  public static <T> String getLogString(@NotNull @NonNls String root, @NotNull Collection<? extends T> changes,
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
      else if (CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(before, after)) {
        return "M: " + getRelativePath(root, after);
      }
      else {
        return "R: " + getRelativePath(root, before) + " -> " + getRelativePath(root, after);
      }
    }, ", ");
  }

  @Nullable
  public static String getRelativePath(@NotNull String root, @NotNull FilePath after) {
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
  @NotNull
  public static Collection<Change> findCorrespondentLocalChanges(@NotNull ChangeListManager changeListManager,
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
    }
  }

  public static void refreshVfsInRoot(@NotNull VirtualFile root) {
    refreshVfsInRoots(Collections.singleton(root));
  }

  public static void refreshVfsInRoots(@NotNull Collection<VirtualFile> roots) {
    RefreshVFsSynchronously.trace("refresh roots " + roots);
    VfsUtil.markDirtyAndRefresh(false, true, false, roots.toArray(VirtualFile.EMPTY_ARRAY));
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

  public static boolean isGitRoot(@NotNull @NonNls String rootDir) {
    try {
      return isGitRoot(Paths.get(rootDir));
    }
    catch (InvalidPathException e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }

  public static boolean isGitRoot(@NotNull Path rootDir) {
    Path dotGit = rootDir.resolve(DOT_GIT);
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(dotGit, BasicFileAttributes.class);
    }
    catch (IOException ignore) {
      return false;
    }

    if (attributes.isDirectory()) {
      try {
        BasicFileAttributes headExists = Files.readAttributes(dotGit.resolve(HEAD_FILE), BasicFileAttributes.class);
        return headExists.isRegularFile();
      }
      catch (IOException ignore) {
        return false;
      }
    }
    if (!attributes.isRegularFile()) {
      return false;
    }

    String content;
    try {
      content = DvcsUtil.tryOrThrow(() -> StringUtil.convertLineSeparators(Files.readString(dotGit)).trim(), dotGit);
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return false;
    }

    String pathToDir = parsePathToRepository(content);
    if (pathToDir == null) return false;
    return findRealRepositoryDir(rootDir, pathToDir) != null;
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

  @NotNull
  public static <T extends GitHandler> T createHandlerWithPaths(@Nullable Collection<? extends FilePath> paths,
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

  @Nullable
  public static Hash getHead(@NotNull GitRepository repository) {
    GitCommandResult result = Git.getInstance().tip(repository, HEAD);
    if (!result.success()) {
      LOG.warn("Couldn't identify the HEAD for " + repository + ": " + result.getErrorOutputAsJoinedString());
      return null;
    }
    String head = result.getOutputAsJoinedString();
    return HashImpl.build(head);
  }
}
