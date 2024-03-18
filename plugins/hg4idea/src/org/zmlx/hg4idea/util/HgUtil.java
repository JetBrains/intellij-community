// Copyright 2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.util;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.FileHistoryPanelImpl;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.ShellCommand;
import org.zmlx.hg4idea.execution.ShellCommandException;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.provider.HgChangeProvider;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HgUtil is a collection of static utility methods for Mercurial.
 */
public abstract class HgUtil {
  public static final Pattern URL_WITH_PASSWORD = Pattern.compile("(?:.+)://(?:.+)(:.+)@(?:.+)");      //http(s)://username:password@url
  public static final int MANY_FILES = 100;
  private static final Logger LOG = Logger.getInstance(HgUtil.class);
  public static final @NlsSafe String DOT_HG = ".hg";
  public static final @NlsSafe String TIP_REFERENCE = "tip";
  public static final @NlsSafe String HEAD_REFERENCE = "HEAD";

  public static File copyResourceToTempFile(String basename, String extension) throws IOException {
    final File tempFile = FileUtil.createTempFile(basename, extension);
    final byte[] buffer = new byte[4096];

    try (InputStream in = HgUtil.class.getClassLoader().getResourceAsStream("python/" + basename + extension);
         OutputStream out = new FileOutputStream(tempFile, false)) {
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
    tempFile.deleteOnExit();
    return tempFile;
  }

  public static void markDirectoryDirty(final Project project, final VirtualFile file) {
    VfsUtil.markDirtyAndRefresh(true, true, false, file);
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
  }

  public static void markFileDirty(final Project project, final VirtualFile file) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().runReadAction(() -> VcsDirtyScopeManager.getInstance(project).fileDirty(file));
    runWriteActionAndWait(() -> file.refresh(true, false));
  }

  /**
   * Runs the given task as a write action in the event dispatching thread and waits for its completion.
   */
  public static void runWriteActionAndWait(final @NotNull Runnable runnable) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(runnable));
  }

  /**
   * Schedules the given task to be run as a write action in the event dispatching thread.
   */
  public static void runWriteActionLater(final @NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(runnable));
  }

  /**
   * Returns a temporary python file that will be deleted on exit.
   * <p>
   * Also all compiled version of the python file will be deleted.
   *
   * @param base The basename of the file to copy
   * @return The temporary copy the specified python file, with all the necessary hooks installed
   * to make sure it is completely removed at shutdown
   */
  public static @Nullable File getTemporaryPythonFile(@NonNls String base) {
    try {
      final File file = copyResourceToTempFile(base, ".py");
      final String fileName = file.getName();
      ShutDownTracker.getInstance().registerShutdownTask(() -> {
        File[] files = file.getParentFile().listFiles((dir, name) -> name.startsWith(fileName));
        if (files != null) {
          for (File file1 : files) {
            file1.delete();
          }
        }
      });
      return file;
    }
    catch (IOException e) {
      return null;
    }
  }


  /**
   * Finds the nearest parent directory which is an hg root.
   *
   * @param dir Directory which parent will be checked.
   * @return Directory which is the nearest hg root being a parent of this directory,
   * or {@code null} if this directory is not under hg.
   * @see com.intellij.openapi.vcs.AbstractVcs#isVersionedDirectory(VirtualFile)
   */
  public static @Nullable VirtualFile getNearestHgRoot(VirtualFile dir) {
    VirtualFile currentDir = dir;
    while (currentDir != null) {
      if (isHgRoot(currentDir)) {
        return currentDir;
      }
      currentDir = currentDir.getParent();
    }
    return null;
  }

  /**
   * Checks if the given directory is an hg root.
   */
  public static boolean isHgRoot(@Nullable VirtualFile dir) {
    return dir != null && dir.findChild(DOT_HG) != null;
  }

  /**
   * Gets the Mercurial root for the given file path or null if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   *
   * @see #getHgRootOrThrow(Project, FilePath)
   */
  public static @Nullable VirtualFile getHgRootOrNull(Project project, FilePath filePath) {
    if (project == null) {
      return getNearestHgRoot(VcsUtil.getVirtualFile(filePath.getPath()));
    }
    return getNearestHgRoot(VcsUtil.getVcsRootFor(project, filePath));
  }

  /**
   * Get hg roots for paths
   *
   * @param filePaths the context paths
   * @return a set of hg roots
   */
  public static @NotNull Set<VirtualFile> hgRoots(@NotNull Project project, @NotNull Collection<? extends FilePath> filePaths) {
    HashSet<VirtualFile> roots = new HashSet<>();
    for (FilePath path : filePaths) {
      ContainerUtil.addIfNotNull(roots, getHgRootOrNull(project, path));
    }
    return roots;
  }

  /**
   * Gets the Mercurial root for the given file path or null if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   *
   * @see #getHgRootOrThrow(Project, FilePath)
   * @see #getHgRootOrNull(Project, FilePath)
   */
  public static @Nullable VirtualFile getHgRootOrNull(Project project, @NotNull VirtualFile file) {
    return getHgRootOrNull(project, VcsUtil.getFilePath(file.getPath()));
  }

  /**
   * Gets the Mercurial root for the given file path or throws a VcsException if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   *
   * @see #getHgRootOrNull(Project, FilePath)
   */
  public static @NotNull VirtualFile getHgRootOrThrow(Project project, FilePath filePath) throws VcsException {
    final VirtualFile vf = getHgRootOrNull(project, filePath);
    if (vf == null) {
      throw new VcsException(HgBundle.message("hg4idea.exception.file.not.under.hg", filePath.getPresentableUrl()));
    }
    return vf;
  }

  public static @NotNull VirtualFile getHgRootOrThrow(Project project, VirtualFile file) throws VcsException {
    return getHgRootOrThrow(project, VcsUtil.getFilePath(file.getPath()));
  }

  /**
   * Shows a message dialog to enter the name of new branch.
   *
   * @return name of new branch or {@code null} if user has cancelled the dialog.
   */
  public static @Nullable String getNewBranchNameFromUser(@NotNull HgRepository repository,
                                                @DialogTitle @NotNull String dialogTitle) {
    return Messages.showInputDialog(repository.getProject(), HgBundle.message("hg4idea.branch.enter.name"), dialogTitle,
                                    Messages.getQuestionIcon(), "",
                                    new HgBranchReferenceValidator(repository));
  }

  /**
   * Groups the given files by their Mercurial repositories and returns the map of relative paths to files for each repository.
   *
   * @param hgFiles files to be grouped.
   * @return key is repository, values is the non-empty list of relative paths to files, which belong to this repository.
   */
  public static @NotNull Map<VirtualFile, List<String>> getRelativePathsByRepository(Collection<? extends HgFile> hgFiles) {
    final Map<VirtualFile, List<String>> map = new HashMap<>();
    if (hgFiles == null) {
      return map;
    }
    for (HgFile file : hgFiles) {
      final VirtualFile repo = file.getRepo();
      List<String> files = map.get(repo);
      if (files == null) {
        files = new ArrayList<>();
        map.put(repo, files);
      }
      files.add(file.getRelativePath());
    }
    return map;
  }

  public static @NotNull HgFile getFileNameInTargetRevision(Project project, HgRevisionNumber vcsRevisionNumber, HgFile localHgFile) {
    //get file name in target revision if it was moved/renamed
    // if file was moved but not committed then hg status would return nothing, so it's better to point working dir as '.' revision
    HgStatusCommand statCommand = new HgStatusCommand.Builder(false).copySource(true).baseRevision(vcsRevisionNumber).
      targetRevision(HgRevisionNumber.getInstance("", ".")).build(project);

    Set<HgChange> changes = statCommand.executeInCurrentThread(localHgFile.getRepo(), Collections.singletonList(localHgFile.toFilePath()));

    for (HgChange change : changes) {
      if (change.afterFile().equals(localHgFile)) {
        return change.beforeFile();
      }
    }
    return localHgFile;
  }

  public static @NotNull FilePath getOriginalFileName(@NotNull FilePath filePath, ChangeListManager changeListManager) {
    Change change = changeListManager.getChange(filePath);
    if (change == null) {
      return filePath;
    }

    FileStatus status = change.getFileStatus();
    if (status == HgChangeProvider.FileStatuses.COPIED ||
        status == HgChangeProvider.FileStatuses.RENAMED) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      assert beforeRevision != null : "If a file's status is copied or renamed, there must be an previous version";
      return beforeRevision.getFile();
    }
    else {
      return filePath;
    }
  }

  public static @NotNull Map<VirtualFile, Collection<VirtualFile>> sortByHgRoots(@NotNull Project project,
                                                                                 @NotNull Collection<? extends VirtualFile> files) {
    Map<VirtualFile, Collection<VirtualFile>> sorted = new HashMap<>();
    HgRepositoryManager repositoryManager = getRepositoryManager(project);
    for (VirtualFile file : files) {
      HgRepository repo = repositoryManager.getRepositoryForFile(file);
      if (repo == null) {
        continue;
      }
      Collection<VirtualFile> filesForRoot = sorted.get(repo.getRoot());
      if (filesForRoot == null) {
        filesForRoot = new HashSet<>();
        sorted.put(repo.getRoot(), filesForRoot);
      }
      filesForRoot.add(file);
    }
    return sorted;
  }

  @RequiresBackgroundThread
  public static @NotNull Map<VirtualFile, Collection<FilePath>> groupFilePathsByHgRoots(@NotNull Project project,
                                                                                        @NotNull Collection<? extends FilePath> files) {
    Map<VirtualFile, Collection<FilePath>> sorted = new HashMap<>();
    if (project.isDisposed()) return sorted;
    HgRepositoryManager repositoryManager = getRepositoryManager(project);
    for (FilePath file : files) {
      HgRepository repo = repositoryManager.getRepositoryForFile(file);
      if (repo == null) {
        continue;
      }
      Collection<FilePath> filesForRoot = sorted.get(repo.getRoot());
      if (filesForRoot == null) {
        filesForRoot = new HashSet<>();
        sorted.put(repo.getRoot(), filesForRoot);
      }
      filesForRoot.add(file);
    }
    return sorted;
  }

  /**
   * Convert {@link VcsVirtualFile} to the {@link LocalFileSystem local} Virtual File.
   * <p>
   * TODO
   * It is a workaround for the following problem: VcsVirtualFiles returned from the {@link FileHistoryPanelImpl} contain the current path
   * of the file, not the path that was in certain revision. This has to be fixed by making {@link HgFileRevision} implement
   * {@link VcsFileRevisionEx}.
   */
  public static @Nullable VirtualFile convertToLocalVirtualFile(@Nullable VirtualFile file) {
    if (!(file instanceof AbstractVcsVirtualFile)) {
      return file;
    }
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile resultFile = lfs.findFileByPath(file.getPath());
    if (resultFile == null) {
      resultFile = lfs.refreshAndFindFileByPath(file.getPath());
    }
    return resultFile;
  }

  public static @NotNull List<Change> getDiff(final @NotNull Project project,
                                              final @NotNull VirtualFile root,
                                              final @NotNull FilePath path,
                                              final @Nullable HgRevisionNumber revNum1,
                                              final @Nullable HgRevisionNumber revNum2) {
    HgStatusCommand statusCommand;
    if (revNum1 != null) {
      //rev2==null means "compare with local version"
      statusCommand = new HgStatusCommand.Builder(true).ignored(false).unknown(false).copySource(!path.isDirectory()).baseRevision(revNum1)
        .targetRevision(revNum2).build(project);
    }
    else {
      LOG.assertTrue(revNum2 != null, "revision1 and revision2 can't both be null. Path: " + path); //rev1 and rev2 can't be null both//
      //get initial changes//
      statusCommand =
        new HgStatusCommand.Builder(true).ignored(false).unknown(false).copySource(false).baseRevision(revNum2)
          .build(project);
    }

    Collection<HgChange> hgChanges = statusCommand.executeInCurrentThread(root, Collections.singleton(path));
    return createChanges(project, root, revNum1, revNum2, hgChanges);
  }

  public static @NotNull List<Change> createChanges(@NotNull Project project,
                                                    @NotNull VirtualFile root,
                                                    @Nullable HgRevisionNumber revNum1,
                                                    @Nullable HgRevisionNumber revNum2, Collection<HgChange> hgChanges) {
    List<Change> changes = new ArrayList<>();
    //convert output changes to standard Change class
    for (HgChange hgChange : hgChanges) {
      FileStatus status = convertHgDiffStatus(hgChange.getStatus());
      if (status != FileStatus.UNKNOWN) {
        changes.add(HgHistoryUtil.createChange(project, root, hgChange.beforeFile().getRelativePath(), revNum1,
                                               hgChange.afterFile().getRelativePath(), revNum2, status));
      }
    }
    return changes;
  }

  public static @NotNull FileStatus convertHgDiffStatus(@NotNull HgFileStatusEnum hgstatus) {
    if (hgstatus.equals(HgFileStatusEnum.ADDED)) {
      return FileStatus.ADDED;
    }
    else if (hgstatus.equals(HgFileStatusEnum.DELETED)) {
      return FileStatus.DELETED;
    }
    else if (hgstatus.equals(HgFileStatusEnum.MODIFIED)) {
      return FileStatus.MODIFIED;
    }
    else if (hgstatus.equals(HgFileStatusEnum.COPY)) {
      return HgChangeProvider.FileStatuses.COPIED;
    }
    else if (hgstatus.equals(HgFileStatusEnum.UNVERSIONED)) {
      return FileStatus.UNKNOWN;
    }
    else if (hgstatus.equals(HgFileStatusEnum.IGNORED)) {
      return FileStatus.IGNORED;
    }
    else {
      return FileStatus.UNKNOWN;
    }
  }

  public static byte @NotNull [] loadContent(@NotNull Project project,
                                             @Nullable HgRevisionNumber revisionNumber,
                                             @NotNull HgFile fileToCat) {
    HgCommandResult result = new HgCatCommand(project).execute(fileToCat, revisionNumber, fileToCat.toFilePath().getCharset());
    return result != null && result.getExitValue() == 0 ? result.getBytesOutput() : ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  public static @NlsSafe String removePasswordIfNeeded(@NotNull String path) {
    Matcher matcher = URL_WITH_PASSWORD.matcher(path);
    if (matcher.matches()) {
      return path.substring(0, matcher.start(1)) + path.substring(matcher.end(1));
    }
    return path;
  }

  public static @Nls @NotNull String getDisplayableBranchOrBookmarkText(@NotNull HgRepository repository) {
    HgRepository.State state = repository.getState();

    String branchName = StringUtil.notNullize(repository.getCurrentBranchName());

    if (state == HgRepository.State.MERGING) {
      return HgBundle.message("hg4idea.status.bar.widget.text.merge", branchName);
    }
    else if (state == HgRepository.State.REBASING) {
      return HgBundle.message("hg4idea.status.bar.widget.text.rebase", branchName);
    }
    else if (state == HgRepository.State.GRAFTING) {
      return HgBundle.message("hg4idea.status.bar.widget.text.graft", branchName);
    }
    else {
      return branchName;
    }
  }

  public static @NotNull HgRepositoryManager getRepositoryManager(@NotNull Project project) {
    return project.getService(HgRepositoryManager.class);
  }

  /**
   * @deprecated Prefer {@link #guessWidgetRepository(Project)} or {@link #guessRepositoryForOperation(Project, DataContext)}.
   */
  @Deprecated
  @RequiresEdt
  public static @Nullable HgRepository getCurrentRepository(@NotNull Project project) {
    if (project.isDisposed()) return null;
    return DvcsUtil.guessRepositoryForFile(project, getRepositoryManager(project),
                                           DvcsUtil.getSelectedFile(project),
                                           HgProjectSettings.getInstance(project).getRecentRootPath());
  }

  @CalledInAny
  public static @Nullable HgRepository guessWidgetRepository(@NotNull Project project, @Nullable VirtualFile selectedFile) {
    return DvcsUtil.guessWidgetRepository(project,
                                          HgUtil.getRepositoryManager(project),
                                          HgProjectSettings.getInstance(project).getRecentRootPath(),
                                          selectedFile);
  }

  public static @Nullable HgRepository guessRepositoryForOperation(@NotNull Project project, @NotNull DataContext dataContext) {
    return DvcsUtil.guessRepositoryForOperation(project, HgUtil.getRepositoryManager(project), dataContext);
  }

  public static @Nullable HgRepository getRepositoryForFile(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null || project.isDisposed()) return null;

    HgRepositoryManager repositoryManager = getRepositoryManager(project);
    VirtualFile root = getHgRootOrNull(project, file);
    return repositoryManager.getRepositoryForRoot(root);
  }

  @CalledInAny
  public static @Nullable String getRepositoryDefaultPath(@NotNull Project project, @NotNull VirtualFile root) {
    HgRepository hgRepository = getRepositoryManager(project).getRepositoryForRootQuick(root);
    assert hgRepository != null : "Repository can't be null for root " + root.getName();
    return hgRepository.getRepositoryConfig().getDefaultPath();
  }

  public static @Nullable String getRepositoryDefaultPushPath(@NotNull Project project, @NotNull VirtualFile root) {
    HgRepository hgRepository = getRepositoryManager(project).getRepositoryForRoot(root);
    assert hgRepository != null : "Repository can't be null for root " + root.getName();
    return hgRepository.getRepositoryConfig().getDefaultPushPath();
  }

  public static @Nullable String getConfig(@NotNull Project project,
                                           @NotNull VirtualFile root,
                                           @NotNull String section,
                                           @Nullable String configName) {
    HgRepository hgRepository = getRepositoryManager(project).getRepositoryForRoot(root);
    assert hgRepository != null : "Repository can't be null for root " + root.getName();
    return hgRepository.getRepositoryConfig().getNamedConfig(section, configName);
  }

  @CalledInAny
  public static @NotNull Collection<String> getRepositoryPaths(@NotNull Project project,
                                                               @NotNull VirtualFile root) {
    HgRepository hgRepository = getRepositoryManager(project).getRepositoryForRootQuick(root);
    assert hgRepository != null : "Repository can't be null for root " + root.getName();
    return hgRepository.getRepositoryConfig().getPaths();
  }

  public static boolean isExecutableValid(@NotNull Project project, @Nullable String executable) {
    try {
      if (StringUtil.isEmptyOrSpaces(executable)) {
        return false;
      }
      HgCommandResult result = getVersionOutput(project, executable);
      return result.getExitValue() == 0 && !result.getRawOutput().isEmpty();
    }
    catch (Throwable e) {
      LOG.info("Error during hg executable validation: ", e);
      return false;
    }
  }

  public static @NotNull HgCommandResult getVersionOutput(@NotNull Project project, @NotNull String executable) throws ShellCommandException {
    if (!project.isDefault() && !TrustedProjects.isTrusted(project)) {
      throw new ShellCommandException("Can't run a Hg command in the safe mode");
    }

    String hgExecutable = executable.trim();
    List<String> cmdArgs = new ArrayList<>();
    cmdArgs.add(hgExecutable);
    cmdArgs.add("version");
    cmdArgs.add("-q");
    ShellCommand shellCommand = new ShellCommand(cmdArgs, null, CharsetToolkit.getDefaultSystemCharset());
    return shellCommand.execute(false, false);
  }

  public static List<String> getNamesWithoutHashes(Collection<? extends HgNameWithHashInfo> namesWithHashes) {
    //return names without duplication (actually for several heads in one branch)
    List<String> names = new ArrayList<>();
    for (HgNameWithHashInfo hash : namesWithHashes) {
      if (!names.contains(hash.getName())) {
        names.add(hash.getName());
      }
    }
    return names;
  }

  public static List<String> getSortedNamesWithoutHashes(Collection<? extends HgNameWithHashInfo> namesWithHashes) {
    return StreamEx.of(getNamesWithoutHashes(namesWithHashes)).sorted(StringUtil::naturalCompare).toList();
  }

  public static @NotNull Couple<String> parseUserNameAndEmail(@NotNull String authorString) {
    //special characters should be retained for properly filtering by username. For Mercurial "a.b" username is not equal to "a b"
    // Vasya Pupkin <vasya.pupkin@jetbrains.com> -> Vasya Pupkin , vasya.pupkin@jetbrains.com
    int startEmailIndex = authorString.indexOf('<');
    int startDomainIndex = authorString.indexOf('@');
    int endEmailIndex = authorString.indexOf('>');
    String userName;
    String email;
    if (0 < startEmailIndex && startEmailIndex < startDomainIndex && startDomainIndex < endEmailIndex) {
      email = authorString.substring(startEmailIndex + 1, endEmailIndex);
      userName = authorString.substring(0, startEmailIndex).trim();
    }
    // vasya.pupkin@email.com || <vasya.pupkin@email.com>
    else if (!authorString.contains(" ") && startDomainIndex > 0) { //simple e-mail check. john@localhost
      userName = "";
      if (startEmailIndex >= 0 && startDomainIndex > startEmailIndex && startDomainIndex < endEmailIndex) {
        email = authorString.substring(startEmailIndex + 1, endEmailIndex).trim();
      }
      else {
        email = authorString;
      }
    }

    else {
      userName = authorString.trim();
      email = "";
    }
    return Couple.of(userName, email);
  }

  public static @NotNull @Unmodifiable List<String> getTargetNames(@NotNull HgRepository repository) {
    return ContainerUtil.sorted(ContainerUtil.map(repository.getRepositoryConfig().getPaths(), s -> removePasswordIfNeeded(s)));
  }
}
