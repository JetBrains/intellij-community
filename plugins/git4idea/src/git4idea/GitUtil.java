/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsFileUtil;
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
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Git utility/helper methods
 */
public class GitUtil {
  /**
   * Comparator for virtual files by name
   */
  public static final Comparator<VirtualFile> VIRTUAL_FILE_COMPARATOR = new Comparator<VirtualFile>() {
    public int compare(final VirtualFile o1, final VirtualFile o2) {
      if (o1 == null && o2 == null) {
        return 0;
      }
      if (o1 == null) {
        return -1;
      }
      if (o2 == null) {
        return 1;
      }
      return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
    }
  };
  /**
   * The UTF-8 encoding name
   */
  public static final String UTF8_ENCODING = "UTF-8";
  /**
   * The UTF8 charset
   */
  public static final Charset UTF8_CHARSET = Charset.forName(UTF8_ENCODING);
  public static final String DOT_GIT = ".git";

  private final static Logger LOG = Logger.getInstance(GitUtil.class);
  private static final int SHORT_HASH_LENGTH = 8;

  public static final Predicate<GitBranchTrackInfo> NOT_NULL_PREDICATE = new Predicate<GitBranchTrackInfo>() {
    @Override
    public boolean apply(@Nullable GitBranchTrackInfo input) {
      return input != null;
    }
  };

  /**
   * A private constructor to suppress instance creation
   */
  private GitUtil() {
    // do nothing
  }

  @Nullable
  public static VirtualFile findGitDir(@NotNull VirtualFile rootDir) {
    VirtualFile child = rootDir.findChild(DOT_GIT);
    if (child == null) {
      return null;
    }
    if (child.isDirectory()) {
      return child;
    }

    // this is standard for submodules, although probably it can
    String content;
    try {
      content = readFile(child);
    }
    catch (IOException e) {
      throw new RuntimeException("Couldn't read " + child, e);
    }
    String pathToDir;
    String prefix = "gitdir:";
    if (content.startsWith(prefix)) {
      pathToDir = content.substring(prefix.length()).trim();
    }
    else {
      pathToDir = content;
    }

    if (!FileUtil.isAbsolute(pathToDir)) {
      String canonicalPath = FileUtil.toCanonicalPath(FileUtil.join(rootDir.getPath(), pathToDir));
      if (canonicalPath == null) {
        return null;
      }
      pathToDir = FileUtil.toSystemIndependentName(canonicalPath);
    }
    return VcsUtil.getVirtualFileWithRefresh(new File(pathToDir));
  }

  /**
   * Makes 3 attempts to get the contents of the file. If all 3 fail with an IOException, rethrows the exception.
   */
  @NotNull
  public static String readFile(@NotNull VirtualFile file) throws IOException {
    final int ATTEMPTS = 3;
    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
      try {
        return new String(file.contentsToByteArray());
      }
      catch (IOException e) {
        LOG.info(String.format("IOException while reading %s (attempt #%s)", file, attempt));
        if (attempt >= ATTEMPTS - 1) {
          throw e;
        }
      }
    }
    throw new AssertionError("Shouldn't get here. Couldn't read " + file);
  }

  /**
   * Sort files by Git root
   *
   * @param virtualFiles files to sort
   * @return sorted files
   * @throws VcsException if non git files are passed
   */
  @NotNull
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(@NotNull Collection<VirtualFile> virtualFiles) throws VcsException {
    return sortFilesByGitRoot(virtualFiles, false);
  }

  /**
   * Sort files by Git root
   *
   * @param virtualFiles files to sort
   * @param ignoreNonGit if true, non-git files are ignored
   * @return sorted files
   * @throws VcsException if non git files are passed when {@code ignoreNonGit} is false
   */
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRoot(Collection<VirtualFile> virtualFiles, boolean ignoreNonGit)
    throws VcsException {
    Map<VirtualFile, List<VirtualFile>> result = new HashMap<VirtualFile, List<VirtualFile>>();
    for (VirtualFile file : virtualFiles) {
      final VirtualFile vcsRoot = gitRootOrNull(file);
      if (vcsRoot == null) {
        if (ignoreNonGit) {
          continue;
        }
        else {
          throw new VcsException("The file " + file.getPath() + " is not under Git");
        }
      }
      List<VirtualFile> files = result.get(vcsRoot);
      if (files == null) {
        files = new ArrayList<VirtualFile>();
        result.put(vcsRoot, files);
      }
      files.add(file);
    }
    return result;
  }

  /**
   * Sort files by vcs root
   *
   * @param files files to sort.
   * @return the map from root to the files under the root
   * @throws VcsException if non git files are passed
   */
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(final Collection<FilePath> files) throws VcsException {
    return sortFilePathsByGitRoot(files, false);
  }

  /**
   * Sort files by vcs root
   *
   * @param files files to sort.
   * @return the map from root to the files under the root
   */
  public static Map<VirtualFile, List<FilePath>> sortGitFilePathsByGitRoot(Collection<FilePath> files) {
    try {
      return sortFilePathsByGitRoot(files, true);
    }
    catch (VcsException e) {
      throw new RuntimeException("Unexpected exception:", e);
    }
  }


  /**
   * Sort files by vcs root
   *
   * @param files        files to sort.
   * @param ignoreNonGit if true, non-git files are ignored
   * @return the map from root to the files under the root
   * @throws VcsException if non git files are passed when {@code ignoreNonGit} is false
   */
  @NotNull
  public static Map<VirtualFile, List<FilePath>> sortFilePathsByGitRoot(@NotNull Collection<FilePath> files, boolean ignoreNonGit)
    throws VcsException {
    Map<VirtualFile, List<FilePath>> rc = new HashMap<VirtualFile, List<FilePath>>();
    for (FilePath p : files) {
      VirtualFile root = getGitRootOrNull(p);
      if (root == null) {
        if (ignoreNonGit) {
          continue;
        }
        else {
          throw new VcsException("The file " + p.getPath() + " is not under Git");
        }
      }
      List<FilePath> l = rc.get(root);
      if (l == null) {
        l = new ArrayList<FilePath>();
        rc.put(root, l);
      }
      l.add(p);
    }
    return rc;
  }

  /**
   * Parse UNIX timestamp as it is returned by the git
   *
   * @param value a value to parse
   * @return timestamp as {@link Date} object
   */
  private static Date parseTimestamp(String value) {
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
   * @return Parsed Date or <code>new Date</code> in the case of error.
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
   * Get git roots from content roots
   *
   * @param roots git content roots
   * @return a content root
   */
  public static Set<VirtualFile> gitRootsForPaths(final Collection<VirtualFile> roots) {
    HashSet<VirtualFile> rc = new HashSet<VirtualFile>();
    for (VirtualFile root : roots) {
      VirtualFile f = root;
      do {
        if (f.findFileByRelativePath(DOT_GIT) != null) {
          rc.add(f);
          break;
        }
        f = f.getParent();
      }
      while (f != null);
    }
    return rc;
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
    return getGitRootOrNull(filePath.getIOFile());
  }

  public static boolean isGitRoot(final File file) {
    return file != null && file.exists() && file.isDirectory() && new File(file, DOT_GIT).exists();
  }

  /**
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @Deprecated
  @Nullable
  public static VirtualFile getGitRootOrNull(final File file) {
    File root = file;
    while (root != null && (!root.exists() || !root.isDirectory() || !new File(root, DOT_GIT).exists())) {
      root = root.getParentFile();
    }
    return root == null ? null : LocalFileSystem.getInstance().findFileByIoFile(root);
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
  @Nullable
  public static VirtualFile gitRootOrNull(final VirtualFile file) {
    if (file instanceof AbstractVcsVirtualFile) {
      return getGitRootOrNull(VcsUtil.getFilePath(file.getPath()));
    }
    VirtualFile root = file;
    while (root != null) {
      if (root.findFileByRelativePath(DOT_GIT) != null) {
        return root;
      }
      root = root.getParent();
    }
    return root;
  }

  /**
   * Get git roots for the project. The method shows dialogs in the case when roots cannot be retrieved, so it should be called
   * from the event dispatch thread.
   *
   * @param project the project
   * @param vcs     the git Vcs
   * @return the list of the roots
   *
   * @deprecated because uses the java.io.File.
   * @use GitRepositoryManager#getRepositoryForFile().
   */
  @NotNull
  public static List<VirtualFile> getGitRoots(Project project, GitVcs vcs) throws VcsException {
    final VirtualFile[] contentRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    if (contentRoots == null || contentRoots.length == 0) {
      throw new VcsException(GitBundle.getString("repository.action.missing.roots.unconfigured.message"));
    }
    final List<VirtualFile> roots = new ArrayList<VirtualFile>(gitRootsForPaths(Arrays.asList(contentRoots)));
    if (roots.size() == 0) {
      throw new VcsException(GitBundle.getString("repository.action.missing.roots.misconfigured"));
    }
    Collections.sort(roots, VIRTUAL_FILE_COMPARATOR);
    return roots;
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

  /**
   * Get git roots for the selected paths
   *
   * @param filePaths the context paths
   * @return a set of git roots
   */
  public static Set<VirtualFile> gitRoots(final Collection<FilePath> filePaths) {
    HashSet<VirtualFile> rc = new HashSet<VirtualFile>();
    for (FilePath path : filePaths) {
      final VirtualFile root = getGitRootOrNull(path);
      if (root != null) {
        rc.add(root);
      }
    }
    return rc;
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
                                              final Consumer<GitSimpleHandler> parametersSpecifier,
                                              final Consumer<GitCommittedChangeList> consumer, boolean skipDiffsForMerge) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setSilent(true);
    h.addParameters("--pretty=format:%x04%x01" + GitChangeUtils.COMMITTED_CHANGELIST_FORMAT, "--name-status");
    parametersSpecifier.consume(h);

    String output = h.run();
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
                                                                   final Consumer<GitSimpleHandler> parametersSpecifier)
    throws VcsException {
    final List<GitCommittedChangeList> rc = new ArrayList<GitCommittedChangeList>();

    getLocalCommittedChanges(project, root, parametersSpecifier, new Consumer<GitCommittedChangeList>() {
      public void consume(GitCommittedChangeList committedChangeList) {
        rc.add(committedChangeList);
      }
    }, false);

    return rc;
  }

  /**
   * <p>Unescape path returned by Git.</p>
   * <p>
   *   If there are quotes in the file name, Git not only escapes them, but also encloses the file name into quotes:
   *   <code>"\"quote"</code>
   * </p>
   * <p>
   *   If there are spaces in the file name, Git displays the name as is, without escaping spaces and without enclosing name in quotes.
   * </p>
   *
   * @param path a path to unescape
   * @return unescaped path ready to be searched in the VFS or file system.
   * @throws com.intellij.openapi.vcs.VcsException if the path in invalid
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
              assert n == b.length;
              // add them to string
              final String encoding = GitConfigUtil.getFileNameEncoding();
              try {
                rc.append(new String(b, encoding));
              }
              catch (UnsupportedEncodingException e1) {
                throw new IllegalStateException("The file name encoding is unsuported: " + encoding);
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
  public static GitRemote findRemoteByName(@NotNull GitRepository repository, @Nullable String name) {
    if (name == null) {
      return null;
    }
    for (GitRemote remote : repository.getRemotes()) {
      if (remote.getName().equals(name)) {
        return remote;
      }
    }
    return null;
  }

  /**
   * @deprecated Calls Git for tracked info, use {@link GitRepository#getBranchTrackInfos()} instead.
   */
  @Nullable
  @Deprecated
  public static Pair<GitRemote, GitRemoteBranch> findMatchingRemoteBranch(GitRepository repository, GitLocalBranch branch)
    throws VcsException {
    /*
    from man git-push:
    git push
               Works like git push <remote>, where <remote> is the current branch's remote (or origin, if no
               remote is configured for the current branch).

     */
    String remoteName = GitBranchUtil.getTrackedRemoteName(repository.getProject(), repository.getRoot(), branch.getName());
    GitRemote remote;
    if (remoteName == null) {
      remote = findOrigin(repository.getRemotes());
    } else {
      remote = findRemoteByName(repository, remoteName);
    }
    if (remote == null) {
      return null;
    }

    for (GitRemoteBranch remoteBranch : repository.getBranches().getRemoteBranches()) {
      if (remoteBranch.getName().equals(remote.getName() + "/" + branch.getName())) {
        return Pair.create(remote, remoteBranch);
      }
    }
    return null;
  }

  @Nullable
  private static GitRemote findOrigin(Collection<GitRemote> remotes) {
    for (GitRemote remote : remotes) {
      if (remote.getName().equals("origin")) {
        return remote;
      }
    }
    return null;
  }

  public static boolean repoContainsRemoteBranch(@NotNull GitRepository repository, @NotNull GitRemoteBranch dest) {
    return repository.getBranches().getRemoteBranches().contains(dest);
  }

  @NotNull
  public static Collection<VirtualFile> getRootsFromRepositories(@NotNull Collection<GitRepository> repositories) {
    Collection<VirtualFile> roots = new ArrayList<VirtualFile>(repositories.size());
    for (GitRepository repository : repositories) {
      roots.add(repository.getRoot());
    }
    return roots;
  }

  @NotNull
  public static Collection<GitRepository> getRepositoriesFromRoots(@NotNull GitRepositoryManager repositoryManager,
                                                                   @NotNull Collection<VirtualFile> roots) {
    Collection<GitRepository> repositories = new ArrayList<GitRepository>(roots.size());
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
   * <code>git diff --name-only master..origin/master</code>
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

    final Collection<String> remoteChanges = new HashSet<String>();
    for (StringScanner s = new StringScanner(result.getOutputAsJoinedString()); s.hasMoreData(); ) {
      final String relative = s.line();
      if (StringUtil.isEmptyOrSpaces(relative)) {
        continue;
      }
      final String path = repository.getRoot().getPath() + "/" + unescapePath(relative);
      remoteChanges.add(FilePathsHelper.convertPath(path));
    }
    return remoteChanges;
  }

  @NotNull
  public static GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return ServiceManager.getService(project, GitRepositoryManager.class);
  }

  @Nullable
  public static GitRepository getRepositoryForRootOrLogError(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepositoryManager manager = getRepositoryManager(project);
    GitRepository repository = manager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository is null for root " + root);
    }
    return repository;
  }

  @NotNull
  public static String getPrintableRemotes(@NotNull Collection<GitRemote> remotes) {
    return StringUtil.join(remotes, new Function<GitRemote, String>() {
      @Override
      public String fun(GitRemote remote) {
        return remote.getName() + ": [" + StringUtil.join(remote.getUrls(), ", ") + "]";
      }
    }, "\n");
  }

  @NotNull
  public static String getShortHash(@NotNull String hash) {
    if (hash.length() == 0) return "";
    if (hash.length() == 40) return hash.substring(0, SHORT_HASH_LENGTH);
    if (hash.length() > 40)  // revision string encoded with date too
    {
      return hash.substring(hash.indexOf("[") + 1, SHORT_HASH_LENGTH);
    }
    return hash;
  }

  @NotNull
  public static String fileOrFolder(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      return "Folder";
    }
    else {
      return "File";
    }
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
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          VirtualFile vcsRoot = getGitRoot(file);
          final CommittedChangeList changeList = GitChangeUtils.getRevisionChanges(project, vcsRoot, revision, true, local, revertable);
          if (changeList != null) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                AbstractVcsHelper.getInstance(project).showChangesListBrowser(changeList,
                                                                              GitBundle.message("paths.affected.title", revision));
              }
            });
          }
        }
        catch (final VcsException e) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              GitUIUtil.showOperationError(project, e, "git show");
            }
          });
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

  @NotNull
  public static Collection<GitRepository> getRepositoriesForFiles(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    final GitRepositoryManager manager = getRepositoryManager(project);
    com.google.common.base.Function<VirtualFile,GitRepository> ROOT_TO_REPO =
      new com.google.common.base.Function<VirtualFile, GitRepository>() {
        @Override
        public GitRepository apply(@Nullable VirtualFile root) {
          return root != null ? manager.getRepositoryForRoot(root) : null;
        }
      };
    return Collections2.filter(Collections2.transform(sortFilesByGitRootsIgnoringOthers(files).keySet(), ROOT_TO_REPO),
                               Predicates.notNull());
  }

  @NotNull
  public static Map<VirtualFile, List<VirtualFile>> sortFilesByGitRootsIgnoringOthers(@NotNull Collection<VirtualFile> files) {
    try {
      return sortFilesByGitRoot(files, true);
    }
    catch (VcsException e) {
      LOG.error("Should never happen, since we passed 'ignore non-git' parameter", e);
      return Collections.emptyMap();
    }
  }

  /**
   * git diff --name-only [--cached]
   * @return true if there is anything in the unstaged/staging area, false if the unstraed/staging area is empty.
   * @param staged if true checks the staging area, if false checks unstaged files.
   * @param project
   * @param root
   */
  public static boolean hasLocalChanges(boolean staged, Project project, VirtualFile root) throws VcsException {
    final GitSimpleHandler diff = new GitSimpleHandler(project, root, GitCommand.DIFF);
    diff.addParameters("--name-only");
    if (staged) {
      diff.addParameters("--cached");
    }
    diff.setStdoutSuppressed(true);
    diff.setStderrSuppressed(true);
    diff.setSilent(true);
    final String output = diff.run();
    return !output.trim().isEmpty();
  }

}
