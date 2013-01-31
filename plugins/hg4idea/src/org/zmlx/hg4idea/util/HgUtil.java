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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.FileHistoryPanelImpl;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgRemoveCommand;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.provider.HgChangeProvider;

import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HgUtil is a collection of static utility methods for Mercurial.
 */
public abstract class HgUtil {

  public static final Pattern URL_WITH_PASSWORD = Pattern.compile("(?:.+)://(?:.+)(:.+)@(?:.+)");      //http(s)://username:password@url
  public static final int MANY_FILES = 100;
  private static final Logger LOG = Logger.getInstance(HgUtil.class);

  public static File copyResourceToTempFile(String basename, String extension) throws IOException {
    final InputStream in = HgUtil.class.getClassLoader().getResourceAsStream("python/" + basename + extension);

    final File tempFile = FileUtil.createTempFile(basename, extension);
    final byte[] buffer = new byte[4096];

    OutputStream out = null;
    try {
      out = new FileOutputStream(tempFile, false);
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1)
        out.write(buffer, 0, bytesRead);
    } finally {
      try {
        out.close();
      }
      catch (IOException e) {
        // ignore
      }
    }
    try {
      in.close();
    }
    catch (IOException e) {
      // ignore
    }
    tempFile.deleteOnExit();
    return tempFile;
  }

  public static void markDirectoryDirty(final Project project, final VirtualFile file) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
      }
    });
    runWriteActionAndWait(new Runnable() {
      public void run() {
        file.refresh(true, true);
      }
    });
  }

  public static void markFileDirty( final Project project, final VirtualFile file ) throws InvocationTargetException, InterruptedException {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).fileDirty(file);
      }
    } );
    runWriteActionAndWait(new Runnable() {
      public void run() {
        file.refresh(true, false);
      }
    });
  }

  /**
   * Runs the given task as a write action in the event dispatching thread and waits for its completion.
   */
  public static void runWriteActionAndWait(@NotNull final Runnable runnable) throws InvocationTargetException, InterruptedException {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    });
  }

  /**
   * Schedules the given task to be run as a write action in the event dispatching thread.
   */
  public static void runWriteActionLater(@NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    });
  }

  /**
   * Returns a temporary python file that will be deleted on exit.
   *
   * Also all compiled version of the python file will be deleted.
   *
   * @param base The basename of the file to copy
   * @return The temporary copy the specified python file, with all the necessary hooks installed
   * to make sure it is completely removed at shutdown
   */
  @Nullable
  public static File getTemporaryPythonFile(String base) {
    try {
      final File file = copyResourceToTempFile(base, ".py");
      final String fileName = file.getName();
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        public void run() {
          File[] files = file.getParentFile().listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return name.startsWith(fileName);
            }
          });
          if (files != null) {
            for (File file1 : files) {
              file1.delete();
            }
          }
        }
      });
      return file;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Calls 'hg remove' to remove given files from the VCS.
   * @param project
   * @param files files to be removed from the VCS.
   */
  public static void removeFilesFromVcs(Project project, List<FilePath> files) {
    final HgRemoveCommand command = new HgRemoveCommand(project);
    for (FilePath filePath : files) {
      final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
      if (vcsRoot == null) {
        continue;
      }
      command.execute(new HgFile(vcsRoot, filePath));
    }
  }


  /**
   * Finds the nearest parent directory which is an hg root.
   * @param dir Directory which parent will be checked.
   * @return Directory which is the nearest hg root being a parent of this directory,
   * or <code>null</code> if this directory is not under hg.
   * @see com.intellij.openapi.vcs.AbstractVcs#isVersionedDirectory(com.intellij.openapi.vfs.VirtualFile)
   */
  @Nullable
  public static VirtualFile getNearestHgRoot(VirtualFile dir) {
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
  public static boolean isHgRoot(VirtualFile dir) {
    return dir.findChild(".hg") != null;
  }

  /**
   * Gets the Mercurial root for the given file path or null if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   * @see #getHgRootOrThrow(com.intellij.openapi.project.Project, com.intellij.openapi.vcs.FilePath)
   */
  @Nullable
  public static VirtualFile getHgRootOrNull(Project project, FilePath filePath) {
    if (project == null) {
      return getNearestHgRoot(VcsUtil.getVirtualFile(filePath.getPath()));
    }
    return getNearestHgRoot(VcsUtil.getVcsRootFor(project, filePath));
  }

  /**
   * Gets the Mercurial root for the given file path or null if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   * @see #getHgRootOrThrow(com.intellij.openapi.project.Project, com.intellij.openapi.vcs.FilePath)
   * @see #getHgRootOrNull(com.intellij.openapi.project.Project, com.intellij.openapi.vcs.FilePath)
   */
  @Nullable
  public static VirtualFile getHgRootOrNull(Project project, VirtualFile file) {
    return getHgRootOrNull(project, VcsUtil.getFilePath(file.getPath()));
  }

  /**
   * Gets the Mercurial root for the given file path or throws a VcsException if non exists:
   * the root should not only be in directory mappings, but also the .hg repository folder should exist.
   * @see #getHgRootOrNull(com.intellij.openapi.project.Project, com.intellij.openapi.vcs.FilePath)
   */
  @NotNull
  public static VirtualFile getHgRootOrThrow(Project project, FilePath filePath) throws VcsException {
    final VirtualFile vf = getHgRootOrNull(project, filePath);
    if (vf == null) {
      throw new VcsException(HgVcsMessages.message("hg4idea.exception.file.not.under.hg", filePath.getPresentableUrl()));
    }
    return vf;
  }

  @NotNull
  public static VirtualFile getHgRootOrThrow(Project project, VirtualFile file) throws VcsException {
    return getHgRootOrThrow(project, VcsUtil.getFilePath(file.getPath()));
  }

  /**
   * Checks is a merge operation is in progress on the given repository.
   * Actually gets the number of parents of the current revision. If there are 2 parents, then a merge is going on. Otherwise there is
   * only one parent.
   * @param project    project to work on.
   * @param repository repository which is checked on merge.
   * @return True if merge operation is in progress, false if there is no merge operation.
   */
  public static boolean isMergeInProgress(@NotNull Project project, VirtualFile repository) {
    return new HgWorkingCopyRevisionsCommand(project).parents(repository).size() > 1;
  }
  /**
   * Groups the given files by their Mercurial repositories and returns the map of relative paths to files for each repository.
   * @param hgFiles files to be grouped.
   * @return key is repository, values is the non-empty list of relative paths to files, which belong to this repository.
   */
  @NotNull
  public static Map<VirtualFile, List<String>> getRelativePathsByRepository(Collection<HgFile> hgFiles) {
    final Map<VirtualFile, List<String>> map = new HashMap<VirtualFile, List<String>>();
    if (hgFiles == null) {
      return map;
    }
    for(HgFile file : hgFiles) {
      final VirtualFile repo = file.getRepo();
      List<String> files = map.get(repo);
      if (files == null) {
        files = new ArrayList<String>();
        map.put(repo, files);
      }
      files.add(file.getRelativePath());
    }
    return map;
  }


  public static HgFile getFileNameInTargetRevision(Project project, HgRevisionNumber vcsRevisionNumber, HgFile localHgFile) {
    HgStatusCommand statCommand = new HgStatusCommand(project);
    statCommand.setIncludeUnknown(false);
    statCommand.setBaseRevision(vcsRevisionNumber);
    statCommand.setIncludeCopySource(true);

    Set<HgChange> changes = statCommand.execute(localHgFile.getRepo());

    for (HgChange change : changes) {
      if (change.afterFile().equals(localHgFile)) {
        return change.beforeFile();
      }
    }
    return localHgFile;
  }

  @NotNull
  public static FilePath getOriginalFileName(@NotNull FilePath filePath, ChangeListManager changeListManager) {
    Change change = changeListManager.getChange(filePath);
    if (change == null) {
      return filePath;
    }

    FileStatus status = change.getFileStatus();
    if (status == HgChangeProvider.COPIED ||
        status == HgChangeProvider.RENAMED) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      assert beforeRevision != null : "If a file's status is copied or renamed, there must be an previous version";
      return beforeRevision.getFile();
    }
    else {
      return filePath;
    }
  }

  /**
   * Returns all HG roots in the project.
   */
  public static @NotNull List<VirtualFile> getHgRepositories(@NotNull Project project) {
    final List<VirtualFile> repos = new LinkedList<VirtualFile>();
    for (VcsRoot root : ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()) {
      if (HgVcs.VCS_NAME.equals(root.getVcs().getName())) {
        repos.add(root.getPath());
      }
    }
    return repos;
  }

  public static @NotNull Map<VirtualFile, Collection<VirtualFile>> sortByHgRoots(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    Map<VirtualFile, Collection<VirtualFile>> sorted = new HashMap<VirtualFile, Collection<VirtualFile>>();
    for (VirtualFile file : files) {
      VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
      if (repo == null) {
        continue;
      }
      Collection<VirtualFile> filesForRoot = sorted.get(repo);
      if (filesForRoot == null) {
        filesForRoot = new HashSet<VirtualFile>();
        sorted.put(repo, filesForRoot);
      }
      filesForRoot.add(file);
    }
    return sorted;
  }

  public static @NotNull Map<VirtualFile, Collection<FilePath>> groupFilePathsByHgRoots(@NotNull Project project,
                                                                                        @NotNull Collection<FilePath> files) {
    Map<VirtualFile, Collection<FilePath>> sorted = new HashMap<VirtualFile, Collection<FilePath>>();
    for (FilePath file : files) {
      VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
      if (repo == null) {
        continue;
      }
      Collection<FilePath> filesForRoot = sorted.get(repo);
      if (filesForRoot == null) {
        filesForRoot = new HashSet<FilePath>();
        sorted.put(repo, filesForRoot);
      }
      filesForRoot.add(file);
    }
    return sorted;
  }

  public static void executeOnPooledThreadIfNeeded(Runnable runnable) {
    if (EventQueue.isDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    } else {
      runnable.run();
    }
  }

  /**
   * Convert {@link VcsVirtualFile} to the {@link LocalFileSystem local} Virtual File.
   *
   * TODO
   * It is a workaround for the following problem: VcsVirtualFiles returned from the {@link FileHistoryPanelImpl} contain the current path
   * of the file, not the path that was in certain revision. This has to be fixed by making {@link HgFileRevision} implement
   * {@link VcsFileRevisionEx}.
   */
  @Nullable
  public static VirtualFile convertToLocalVirtualFile(@Nullable VirtualFile file) {
    if (!(file instanceof VcsVirtualFile)) {
      return file;
    }
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile resultFile = lfs.findFileByPath(file.getPath());
    if (resultFile == null) {
      resultFile = lfs.refreshAndFindFileByPath(file.getPath());
    }
    return resultFile;
  }

  @NotNull
  public static List<Change> getDiff(@NotNull final Project project,
                                     @NotNull final VirtualFile root,
                                     @NotNull final FilePath path,
                                     @Nullable final HgFileRevision rev1,
                                     @Nullable final HgFileRevision rev2, HgPlatformFacade facade) {
    HgStatusCommand statusCommand = new HgStatusCommand(project,facade);
    statusCommand.setIncludeCopySource(false);
    HgRevisionNumber revNumber1 = null;
    if (rev1 != null) {
      revNumber1 = rev1.getRevisionNumber();
      statusCommand.setBaseRevision(revNumber1);
      statusCommand.setTargetRevision(rev2 != null ? rev2.getRevisionNumber() : null);   //rev2==null means "compare with local version"
    }
    else {
      LOG.assertTrue(rev2 != null, "revision1 and revision2 can't both be null. Path: " + path); //rev1 and rev2 can't be null both//
      statusCommand.setBaseRevision(rev2.getRevisionNumber());     //get initial changes//
    }

    Collection<HgChange> hgChanges = statusCommand.execute(root, Collections.singleton(path));
    List<Change> changes = new ArrayList<Change>();
    //convert output changes to standart Change class
    for (HgChange hgChange : hgChanges) {
      FileStatus status = convertHgDiffStatus(hgChange.getStatus());
      if (status != FileStatus.UNKNOWN && status!= FileStatus.IGNORED) {
        changes.add(createChange(project, root, hgChange.beforeFile().getRelativePath(), revNumber1,
                                 hgChange.afterFile().getRelativePath(),
                                 rev2 != null ? rev2.getRevisionNumber() : null, status));
      }
    }
    return changes;
  }

  @NotNull
  public static Change createChange(@NotNull final Project project, VirtualFile root,
                                    @NotNull String fileBefore,
                                    @Nullable HgRevisionNumber revisionBefore,
                                    @NotNull String fileAfter,
                                    @Nullable HgRevisionNumber revisionAfter,
                                    @NotNull FileStatus aStatus) {
    HgContentRevision beforeRevision = revisionBefore == null
                                       ? null
                                       : new HgContentRevision(project, new HgFile(root, new File(root.getPath(), fileBefore)),
                                                               revisionBefore);
    if (revisionAfter == null) {
      ContentRevision currentRevision =
        CurrentContentRevision.create(new HgFile(root, new File(root.getPath(), fileBefore)).toFilePath());
      return new Change(beforeRevision, currentRevision, aStatus);
    }
    HgContentRevision afterRevision = new HgContentRevision(project,
                                                            new HgFile(root, new File(root.getPath(), fileAfter)),
                                                            revisionAfter);
    return new Change(beforeRevision, afterRevision, aStatus);
  }

  @NotNull
  public static FileStatus convertHgDiffStatus(@NotNull HgFileStatusEnum hgstatus) {
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
      return HgChangeProvider.COPIED;
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

  public static String removePasswordIfNeeded(@NotNull String path) {
    Matcher matcher = URL_WITH_PASSWORD.matcher(path);
    if (matcher.matches()) {
      return path.substring(0, matcher.start(1)) + path.substring(matcher.end(1), path.length());
    }
    return path;
  }
}
