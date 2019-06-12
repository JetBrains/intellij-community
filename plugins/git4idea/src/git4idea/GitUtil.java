// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.OpenTHashSet;
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
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitSimplePathsBrowser;
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.openapi.vcs.changes.ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static java.util.Arrays.stream;

/**
 * Git utility/helper methods
 */
public class GitUtil {

  public static final String DOT_GIT = ".git";

  /**
   * This comment char overrides the standard '#' and any other potentially defined by user via {@code core.commentChar}.
   */
  public static final String COMMENT_CHAR = "\u0001";

  public static final String ORIGIN_HEAD = "origin/HEAD";

  public static final String HEAD = "HEAD";
  public static final String CHERRY_PICK_HEAD = "CHERRY_PICK_HEAD";
  public static final String MERGE_HEAD = "MERGE_HEAD";

  private static final String REPO_PATH_LINK_PREFIX = "gitdir:";
  private final static Logger LOG = Logger.getInstance(GitUtil.class);
  private static final String HEAD_FILE = "HEAD";

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
    File file = findRealRepositoryDir(rootDir.getPath(), pathToDir);
    if (file == null) return null;
    return VcsUtil.getVirtualFileWithRefresh(file);
  }

  @Nullable
  private static File findRealRepositoryDir(@NotNull String rootPath, @NotNull String path) {
    if (!FileUtil.isAbsolute(path)) {
      String canonicalPath = FileUtil.toCanonicalPath(FileUtil.join(rootPath, path), true);
      if (canonicalPath == null) {
        return null;
      }
      path = FileUtil.toSystemIndependentName(canonicalPath);
    }
    File file = new File(path);
    return file.isDirectory() ? file : null;
  }

  @NotNull
  private static String parsePathToRepository(@NotNull String content) {
    content = content.trim();
    return content.startsWith(REPO_PATH_LINK_PREFIX) ? content.substring(REPO_PATH_LINK_PREFIX.length()).trim() : content;
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
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Project project,
                                                                       @NotNull Collection<? extends VirtualFile> virtualFiles)
    throws VcsException {
    return sortFilesByGitRoot(project, virtualFiles, false);
  }

  @NotNull
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
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Project project,
                                                                        @NotNull Collection<? extends FilePath> filePaths)
    throws VcsException {
    return sortFilePathsByGitRoot(project, filePaths, false);
  }

  @NotNull
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
  public static Map<GitRepository, List<VirtualFile>> sortFilesByRepository(@NotNull Project project,
                                                                            @NotNull Collection<? extends VirtualFile> filePaths)
    throws VcsException {
    return sortFilesByRepository(project, filePaths, false);
  }

  @NotNull
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
    GitRepositoryManager manager = GitRepositoryManager.getInstance(project);

    Map<VirtualFile, List<FilePath>> result = new HashMap<>();
    for (FilePath path : filePaths) {
      GitRepository repository = manager.getRepositoryForFile(path);
      if (repository == null) {
        if (ignoreNonGit) continue;
        throw new GitRepositoryNotFoundException(path);
      }

      List<FilePath> paths = result.computeIfAbsent(repository.getRoot(), key -> new ArrayList<>());
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
  public static Date parseTimestamp(String value) {
    final long parsed;
    parsed = Long.parseLong(value.trim());
    return new Date(parsed * 1000);
  }

  /**
   * Parse UNIX timestamp returned from Git and handle {@link NumberFormatException} if one happens: return new {@link Date} and
   * log the error properly.
   * In some cases git output gets corrupted and this method is intended to catch the reason, why.
   * @param value      Value to parse.
   * @param handler    Git handler that was called to received the output.
   * @param gitOutput  Git output.
   * @return Parsed Date or {@code new Date} in the case of error.
   */
  public static Date parseTimestampWithNFEReport(String value, GitHandler handler, String gitOutput) {
    try {
      return parseTimestamp(value);
    } catch (NumberFormatException e) {
      LOG.error("annotate(). NFE. Handler: " + handler + ". Output: " + gitOutput, e);
      return  new Date();
    }
  }

  /**
   * Return a git root for the file path (the parent directory with ".git" subdirectory)
   *
   * @param filePath a file path
   * @return git root for the file
   * @throws IllegalArgumentException if the file is not under git
   * @throws VcsException             if the file is not under git
   *
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @Deprecated
  public static VirtualFile getGitRoot(@NotNull FilePath filePath) throws VcsException {
    VirtualFile root = getGitRootOrNull(filePath);
    if (root != null) {
      return root;
    }
    throw new VcsException("The file " + filePath + " is not under git.");
  }

  /**
   * Return a git root for the file path (the parent directory with ".git" subdirectory)
   *
   * @param filePath a file path
   * @return git root for the file or null if the file is not under git
   *
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @Deprecated
  @Nullable
  public static VirtualFile getGitRootOrNull(@NotNull final FilePath filePath) {
    File root = filePath.getIOFile();
    while (root != null) {
      if (isGitRoot(root)) {
        return LocalFileSystem.getInstance().findFileByIoFile(root);
      }
      root = root.getParentFile();
    }
    return null;
  }

  public static boolean isGitRoot(@NotNull File folder) {
    return isGitRoot(folder.getPath());
  }

  public static boolean isGitRoot(@NotNull VirtualFile file) {
    return isGitRoot(file.getPath());
  }

  /**
   * Return a git root for the file (the parent directory with ".git" subdirectory)
   *
   * @param file the file to check
   * @return git root for the file
   * @throws VcsException if the file is not under git
   *
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @Deprecated
  public static VirtualFile getGitRoot(@NotNull final VirtualFile file) throws VcsException {
    final VirtualFile root = gitRootOrNull(file);
    if (root != null) {
      return root;
    }
    else {
      throw new VcsException("The file " + file.getPath() + " is not under git.");
    }
  }

  /**
   * Return a git root for the file (the parent directory with ".git" subdirectory)
   *
   * @param file the file to check
   * @return git root for the file or null if the file is not not under Git
   *
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @Deprecated
  @Nullable
  public static VirtualFile gitRootOrNull(final VirtualFile file) {
    return getGitRootOrNull(VcsUtil.getFilePath(file.getPath()));
  }

  /**
   * Check if the virtual file under git
   *
   * @param vFile a virtual file
   * @return true if the file is under git
   */
  public static boolean isUnderGit(final VirtualFile vFile) {
    return gitRootOrNull(vFile) != null;
  }


  /**
   * Return committer name based on author name and committer name
   *
   * @param authorName    the name of author
   * @param committerName the name of committer
   * @return just a name if they are equal, or name that includes both author and committer
   */
  public static String adjustAuthorName(final String authorName, String committerName) {
    if (!authorName.equals(committerName)) {
      //noinspection HardCodedStringLiteral
      committerName = authorName + ", via " + committerName;
    }
    return committerName;
  }

  /**
   * Check if the file path is under git
   *
   * @param path the path
   * @return true if the file path is under git
   */
  public static boolean isUnderGit(final FilePath path) {
    return getGitRootOrNull(path) != null;
  }

  @NotNull
  public static Set<VirtualFile> getRootsForFilePathsIfAny(@NotNull Project project, @NotNull Collection<? extends FilePath> filePaths) {
    GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
    return filePaths.stream()
      .map(path -> manager.getRepositoryForFile(path))
      .filter(Objects::nonNull)
      .map(Repository::getRoot)
      .collect(Collectors.toSet());
  }

  @NotNull
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
      throw new IllegalStateException("More input is avaialble: " + s.line());
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
   * <p>Unescape path returned by Git.</p>
   * <p>
   *   If there are quotes in the file name, Git not only escapes them, but also encloses the file name into quotes:
   *   {@code "\"quote"}
   * </p>
   * <p>
   *   If there are spaces in the file name, Git displays the name as is, without escaping spaces and without enclosing name in quotes.
   * </p>
   *
   * @param path a path to unescape
   * @return unescaped path ready to be searched in the VFS or file system.
   * @throws VcsException if the path in invalid
   */
  @NotNull
  public static String unescapePath(@NotNull String path) throws VcsException {
    final String QUOTE = "\"";
    if (path.startsWith(QUOTE) && path.endsWith(QUOTE)) {
      path = path.substring(1, path.length() - 1);
    }

    final int l = path.length();
    StringBuilder rc = new StringBuilder(l);
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c == '\\') {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i >= l) {
          throw new VcsException("Unterminated escape sequence in the path: " + path);
        }
        final char e = path.charAt(i);
        switch (e) {
          case '\\':
            rc.append('\\');
            break;
          case 't':
            rc.append('\t');
            break;
          case 'n':
            rc.append('\n');
            break;
          case 'r':
            rc.append('\r');
            break;
          case 'a':
            rc.append('\u0007');
            break;
          case 'b':
            rc.append('\b');
            break;
          case 'f':
            rc.append('\f');
            break;
          case '"':
            rc.append('"');
            break;
          default:
            if (VcsFileUtil.isOctal(e)) {
              // collect sequence of characters as a byte array.
              // count bytes first
              int n = 0;
              for (int j = i; j < l;) {
                if (VcsFileUtil.isOctal(path.charAt(j))) {
                  n++;
                  for (int k = 0; k < 3 && j < l && VcsFileUtil.isOctal(path.charAt(j)); k++) {
                    //noinspection AssignmentToForLoopParameter
                    j++;
                  }
                }
                if (j + 1 >= l || path.charAt(j) != '\\' || !VcsFileUtil.isOctal(path.charAt(j + 1))) {
                  break;
                }
                //noinspection AssignmentToForLoopParameter
                j++;
              }
              // convert to byte array
              byte[] b = new byte[n];
              n = 0;
              while (i < l) {
                if (VcsFileUtil.isOctal(path.charAt(i))) {
                  int code = 0;
                  for (int k = 0; k < 3 && i < l && VcsFileUtil.isOctal(path.charAt(i)); k++) {
                    code = code * 8 + (path.charAt(i) - '0');
                    //noinspection AssignmentToForLoopParameter
                    i++;
                  }
                  b[n++] = (byte)code;
                }
                if (i + 1 >= l || path.charAt(i) != '\\' || !VcsFileUtil.isOctal(path.charAt(i + 1))) {
                  break;
                }
                //noinspection AssignmentToForLoopParameter
                i++;
              }
              //noinspection AssignmentToForLoopParameter
              i--;
              assert n == b.length;
              // add them to string
              final String encoding = GitConfigUtil.getFileNameEncoding();
              try {
                rc.append(new String(b, encoding));
              }
              catch (UnsupportedEncodingException e1) {
                throw new IllegalStateException("The file name encoding is unsupported: " + encoding);
              }
            }
            else {
              throw new VcsException("Unknown escape sequence '\\" + path.charAt(i) + "' in the path: " + path);
            }
        }
      }
      else {
        rc.append(c);
      }
    }
    return rc.toString();
  }

  public static boolean justOneGitRepository(Project project) {
    if (project.isDisposed()) {
      return true;
    }
    GitRepositoryManager manager = getRepositoryManager(project);
    return !manager.moreThanOneRoot();
  }


  @Nullable
  public static GitRemote findRemoteByName(@NotNull GitRepository repository, @NotNull final String name) {
    return findRemoteByName(repository.getRemotes(), name);
  }

  @Nullable
  public static GitRemote findRemoteByName(Collection<GitRemote> remotes, @NotNull final String name) {
    return ContainerUtil.find(remotes, remote -> remote.getName().equals(name));
  }

  @Nullable
  public static GitRemoteBranch findRemoteBranch(@NotNull GitRepository repository,
                                                         @NotNull final GitRemote remote,
                                                         @NotNull final String nameAtRemote) {
    return ContainerUtil.find(repository.getBranches().getRemoteBranches(), remoteBranch -> remoteBranch.getRemote().equals(remote) &&
                                                                                        remoteBranch.getNameForRemoteOperations().equals(GitBranchUtil.stripRefsPrefix(nameAtRemote)));
  }

  @NotNull
  public static GitRemoteBranch findOrCreateRemoteBranch(@NotNull GitRepository repository,
                                                         @NotNull GitRemote remote,
                                                         @NotNull String branchName) {
    GitRemoteBranch remoteBranch = findRemoteBranch(repository, remote, branchName);
    return ObjectUtils.notNull(remoteBranch, new GitStandardRemoteBranch(remote, branchName));
  }

  @NotNull
  public static Collection<VirtualFile> getRootsFromRepositories(@NotNull Collection<? extends GitRepository> repositories) {
    return ContainerUtil.map(repositories, Repository::getRoot);
  }

  @NotNull
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
                                                           @NotNull String beforeRef, @NotNull String afterRef) throws VcsException {
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
  public static GitRepository getRepositoryForFile(@NotNull Project project, @NotNull VirtualFile file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @NotNull
  public static GitRepository getRepositoryForFile(@NotNull Project project, @NotNull FilePath file) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file);
    if (repository == null) throw new GitRepositoryNotFoundException(file);
    return repository;
  }

  @Nullable
  public static GitRepository getRepositoryForRootOrLogError(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository == null) LOG.error(new GitRepositoryNotFoundException(root));
    return repository;
  }

  @NotNull
  public static String getPrintableRemotes(@NotNull Collection<GitRemote> remotes) {
    return StringUtil.join(remotes, remote -> remote.getName() + ": [" + StringUtil.join(remote.getUrls(), ", ") + "]", "\n");
  }

  /**
   * Show changes made in the specified revision.
   *
   * @param project     the project
   * @param revision    the revision number
   * @param file        the file affected by the revision
   * @param local       pass true to let the diff be editable, i.e. making the revision "at the right" be a local (current) revision.
   *                    pass false to let both sides of the diff be non-editable.
   * @param revertable  pass true to let "Revert" action be active.
   */
  public static void showSubmittedFiles(final Project project, final String revision, final VirtualFile file,
                                        final boolean local, final boolean revertable) {
    new Task.Backgroundable(project, GitBundle.message("changes.retrieving", revision)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          VirtualFile vcsRoot = getRepositoryForFile(project, file).getRoot();
          final CommittedChangeList changeList = GitChangeUtils.getRevisionChanges(project, vcsRoot, revision, true, local, revertable);
          UIUtil.invokeLaterIfNeeded(
            () -> AbstractVcsHelper.getInstance(project)
              .showChangesListBrowser(changeList, GitBundle.message("paths.affected.title", revision)));
        }
        catch (final VcsException e) {
          UIUtil.invokeLaterIfNeeded(() -> GitUIUtil.showOperationError(project, e, "git show"));
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
  public static VirtualFile findRefreshFileOrLog(@NotNull String absolutePath) {
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
  public static String toAbsolute(@NotNull VirtualFile root, @NotNull String relativePath) {
    return StringUtil.trimEnd(root.getPath(), "/") + "/" + StringUtil.trimStart(relativePath, "/");
  }

  @NotNull
  public static Collection<String> toAbsolute(@NotNull final VirtualFile root, @NotNull Collection<String> relativePaths) {
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
                                                      @NotNull Collection<String> affectedPaths, boolean relativePaths) {
    ChangeListManagerEx changeListManager = (ChangeListManagerEx)ChangeListManager.getInstance(project);
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

  public static void showPathsInDialog(@NotNull Project project, @NotNull Collection<String> absolutePaths, @NotNull String title,
                                       @Nullable String description) {
    DialogBuilder builder = new DialogBuilder(project);
    builder.setCenterPanel(new GitSimplePathsBrowser(project, absolutePaths));
    if (description != null) {
      builder.setNorthPanel(new MultiLineLabel(description));
    }
    builder.addOkAction();
    builder.setTitle(title);
    builder.show();
  }

  @NotNull
  public static String cleanupErrorPrefixes(@NotNull String msg) {
    final String[] PREFIXES = { "fatal:", "error:" };
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
    return chooseNotNull(getDefaultRemote(remotes), ContainerUtil.getFirstItem(remotes));
  }

  @NotNull
  public static String joinToHtml(@NotNull Collection<? extends GitRepository> repositories) {
    return StringUtil.join(repositories, repository -> repository.getPresentableUrl(), "<br/>");
  }

  @NotNull
  public static String mention(@NotNull GitRepository repository) {
    return getRepositoryManager(repository.getProject()).moreThanOneRoot() ? " in " + getShortRepositoryName(repository) : "";
  }

  @NotNull
  public static String mention(@NotNull Collection<? extends GitRepository> repositories) {
    return mention(repositories, -1);
  }

  @NotNull
  public static String mention(@NotNull Collection<? extends GitRepository> repositories, int limit) {
    if (repositories.isEmpty()) return "";
    return " in " + joinShortNames(repositories, limit);
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
  public static boolean isCaseOnlyChange(@NotNull String oldPath, @NotNull String newPath) {
    if (oldPath.equalsIgnoreCase(newPath)) {
      if (oldPath.equals(newPath)) {
        LOG.info("Comparing perfectly equal paths: " + newPath);
      }
      return true;
    }
    return false;
  }

  @NotNull
  public static String getLogString(@NotNull String root, @NotNull Collection<? extends Change> changes) {
    return getLogString(root, changes, ChangesUtil::getBeforePath, ChangesUtil::getAfterPath);
  }

  @NotNull
  public static <T> String getLogString(@NotNull String root, @NotNull Collection<? extends T> changes,
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
    OpenTHashSet<Change> allChanges = new OpenTHashSet<>(changeListManager.getAllChanges());
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
      VfsUtil.markDirtyAndRefresh(false, true, false, root);
    }
    else {
      RefreshVFsSynchronously.updateChanges(changes);
    }
  }

  public static void updateAndRefreshVfs(@NotNull GitRepository repository, @Nullable Collection<? extends Change> changes) {
    repository.update();
    refreshVfs(repository.getRoot(), changes);
  }

  public static void updateAndRefreshVfs(GitRepository... repositories) {
    // repositories state will be auto-updated with the following VFS refresh => there is no need to call GitRepository#update()
    // but we want repository state to be updated as soon as possible, without waiting for the whole VFS refresh to complete.
    stream(repositories).forEach(GitRepository::update);
    for (GitRepository repository : repositories) {
      refreshVfs(repository.getRoot(), null);
    }
  }

  public static boolean isGitRoot(@NotNull String rootDir) {
    String dotGit = rootDir + File.separatorChar + DOT_GIT;
    FileAttributes attributes = FileSystemUtil.getAttributes(dotGit);
    if (attributes == null) return false;

    if (attributes.isDirectory()) {
      FileAttributes headExists = FileSystemUtil.getAttributes(dotGit + File.separatorChar + HEAD_FILE);
      return headExists != null && headExists.isFile();
    }
    if (!attributes.isFile()) return false;

    String content = DvcsUtil.tryLoadFileOrReturn(new File(dotGit), null, CharsetToolkit.UTF8);
    if (content == null) return false;
    String pathToDir = parsePathToRepository(content);
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

  @NotNull
  public static Map<GitRepository, Hash> getCurrentRevisions(@NotNull Collection<? extends GitRepository> repositories) {
    Map<GitRepository, Hash> result = new LinkedHashMap<>();
    for (GitRepository repository : repositories) {
      String currentRevision = repository.getCurrentRevision();
      if (currentRevision != null) {
        result.put(repository, HashImpl.build(currentRevision));
      }
    }
    return result;
  }

  private static class GitRepositoryNotFoundException extends VcsException {
    private static final String MESSAGE = "Can't find configured git repository for %s";

    private GitRepositoryNotFoundException(@NotNull VirtualFile file) {
      super(String.format(MESSAGE, file.getPresentableUrl()));
    }

    private GitRepositoryNotFoundException(@NotNull FilePath filePath) {
      super(String.format(MESSAGE, filePath.getPresentableUrl()));
    }
  }
}
