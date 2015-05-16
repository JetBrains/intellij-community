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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author max
 */
public class ChangesUtil {
  private static final Key<Boolean> INTERNAL_OPERATION_KEY = Key.create("internal vcs operation");

  private ChangesUtil() {}

  @NotNull
  public static FilePath getFilePath(@NotNull final Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) {
      revision = change.getBeforeRevision();
      assert revision != null;
    }

    return revision.getFile();
  }

  @Nullable
  public static FilePath getBeforePath(@NotNull final Change change) {
    ContentRevision revision = change.getBeforeRevision();
    return revision == null ? null : revision.getFile();
  }

  @Nullable
  public static FilePath getAfterPath(@NotNull final Change change) {
    ContentRevision revision = change.getAfterRevision();
    return revision == null ? null : revision.getFile();
  }

  public static AbstractVcs getVcsForChange(Change change, final Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(getFilePath(change));
  }

  public static AbstractVcs getVcsForFile(VirtualFile file, Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
  }

  public static AbstractVcs getVcsForFile(File file, Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(VcsContextFactory.SERVICE.getInstance().createFilePathOn(file));
  }

  /**
   * TODO: Provide common approach for either case sensitive or case insensitive comparison of File, FilePath, etc. depending on used VCS,
   * TODO: OS, VCS operation (several hashing and equality strategies seems to be useful here)
   */
  @Deprecated
  public static class CaseSensitiveFilePathList {
    @NotNull private final List<FilePath> myResult = new ArrayList<FilePath>();
    @NotNull private final Set<String> myDuplicatesControlSet = new HashSet<String>();

    public void add(@NotNull FilePath file) {
      final String path = file.getIOFile().getAbsolutePath();
      if (! myDuplicatesControlSet.contains(path)) {
        myResult.add(file);
        myDuplicatesControlSet.add(path);
      }
    }

    public void addParents(@NotNull FilePath file, @NotNull Condition<FilePath> condition) {
      FilePath parent = file.getParentPath();

      if (parent != null && condition.value(parent)) {
        add(parent);
        addParents(parent, condition);
      }
    }

    @NotNull
    public List<FilePath> getResult() {
      return myResult;
    }
  }

  @NotNull
  public static List<FilePath> getPaths(@NotNull Collection<Change> changes) {
    return getPathsList(changes).getResult();
  }

  @NotNull
  public static CaseSensitiveFilePathList getPathsList(@NotNull Collection<Change> changes) {
    CaseSensitiveFilePathList list = new CaseSensitiveFilePathList();

    for (Change change : changes) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        list.add(beforeRevision.getFile());
      }
      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        list.add(afterRevision.getFile());
      }
    }

    return list;
  }

  public static List<File> getIoFilesFromChanges(final Collection<Change> changes) {
    // further should contain paths
    final List<File> result = new ArrayList<File>();
    for (Change change : changes) {
      if (change.getAfterRevision() != null) {
        final File ioFile = change.getAfterRevision().getFile().getIOFile();
        if (! result.contains(ioFile)) {
          result.add(ioFile);
        }
      }
      if (change.getBeforeRevision() != null) {
        final File ioFile = change.getBeforeRevision().getFile().getIOFile();
        if (! result.contains(ioFile)) {
          result.add(ioFile);
        }
      }
    }
    return result;
  }

  public static VirtualFile[] getFilesFromChanges(final Collection<Change> changes) {
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        FilePath filePath = afterRevision.getFile();
        VirtualFile file = filePath.getVirtualFile();
        if (file == null || !file.isValid()) {
          file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
        }
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  public static Navigatable[] getNavigatableArray(final Project project, final VirtualFile[] selectedFiles) {
    List<Navigatable> result = new ArrayList<Navigatable>();
    for (VirtualFile selectedFile : selectedFiles) {
      if (!selectedFile.isDirectory()) {
        result.add(new OpenFileDescriptor(project, selectedFile));
      }
    }
    return result.toArray(new Navigatable[result.size()]);
  }

  public static boolean allChangesInOneListOrWholeListsSelected(@NotNull final Project project, @NotNull Change[] changes) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    if (clManager.getChangeListNameIfOnlyOne(changes) != null) return true;
    final List<LocalChangeList> list = clManager.getChangeListsCopy();

    final HashSet<Change> checkSet = new HashSet<Change>();
    ContainerUtil.addAll(checkSet, changes);
    for (LocalChangeList localChangeList : list) {
      final Collection<Change> listChanges = localChangeList.getChanges();
      if (listChanges.isEmpty()) continue;
      Change first = listChanges.iterator().next();
      if (checkSet.contains(first)) {
        for (Change change : listChanges) {
          if (!checkSet.contains(change)) return false;
        }
      } else {
        for (Change change : listChanges) {
          if (checkSet.contains(change)) return false;
        }
      }
    }
    return true;
  }

  @Nullable
  public static ChangeList getChangeListIfOnlyOne(@NotNull final Project project, @Nullable Change[] changes) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);

    final String name = clManager.getChangeListNameIfOnlyOne(changes);
    return (name == null) ? null : clManager.findChangeList(name);
  }

  public static FilePath getCommittedPath(final Project project, FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ChangeListManager.getInstance(project).getChange(filePath);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          afterRevision.getFile().equals(filePath)) {
        filePath = beforeRevision.getFile();
      }
    }
    return filePath;
  }

  public static FilePath getLocalPath(final Project project, final FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ApplicationManager.getApplication().runReadAction(new Computable<Change>() {
      @Override
      @Nullable
      public Change compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return ChangeListManager.getInstance(project).getChange(filePath);
      }
    });
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          beforeRevision.getFile().equals(filePath)) {
        return afterRevision.getFile();
      }
    }
    return filePath;
  }

  @Nullable
  public static VirtualFile findValidParentUnderReadAction(final FilePath file) {
    if (file.getVirtualFile() != null) return file.getVirtualFile();
    final Computable<VirtualFile> computable = new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return findValidParent(file);
      }
    };
    final Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return computable.compute();
    } else {
      return application.runReadAction(computable);
    }
  }

  @Nullable
  public static VirtualFile findValidParentAccurately(final FilePath filePath) {
    if (filePath.getVirtualFile() != null) return filePath.getVirtualFile();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile result = lfs.findFileByIoFile(filePath.getIOFile());
    if (result != null) return result;
    if (! ApplicationManager.getApplication().isReadAccessAllowed()) {
      result = lfs.refreshAndFindFileByIoFile(filePath.getIOFile());
      if (result != null) return result;
    }
    return getValidParentUnderReadAction(filePath);
  }

  @Nullable
  private static VirtualFile getValidParentUnderReadAction(final FilePath filePath) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return findValidParent(filePath);
      }
    });
  }

  /**
   * @deprecated use {@link #findValidParentAccurately(com.intellij.openapi.vcs.FilePath)}
   */
  @Nullable
  @Deprecated
  public static VirtualFile findValidParent(FilePath file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    VirtualFile parent = file.getVirtualFile();
    if (parent == null) {
      parent = file.getVirtualFileParent();
    }
    if (parent == null) {
      File ioFile = file.getIOFile();
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      do {
        parent = lfs.findFileByIoFile(ioFile);
        if (parent != null) break;
        ioFile = ioFile.getParentFile();
        if (ioFile == null) return null;
      }
      while (true);
    }
    return parent;
  }

  @Nullable
  public static String getProjectRelativePath(final Project project, @Nullable final File fileName) {
    if (fileName == null) return null;
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) return fileName.toString();
    String relativePath = FileUtil.getRelativePath(new File(baseDir.getPath()), fileName);
    if (relativePath != null) return relativePath;
    return fileName.toString();
  }

  public static boolean isBinaryContentRevision(final ContentRevision revision) {
    return revision != null && !revision.getFile().isDirectory() && revision instanceof BinaryContentRevision;
  }

  public static boolean isBinaryChange(final Change change) {
    return isBinaryContentRevision(change.getBeforeRevision()) || isBinaryContentRevision(change.getAfterRevision());
  }

  public static boolean isTextConflictingChange(final Change change) {
    final FileStatus status = change.getFileStatus();
    return FileStatus.MERGED_WITH_CONFLICTS.equals(status) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status);
  }

  public static boolean isPropertyConflictingChange(final Change change) {
    final FileStatus status = change.getFileStatus();
    return FileStatus.MERGED_WITH_PROPERTY_CONFLICTS.equals(status) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status);
  }

  public interface PerVcsProcessor<T> {
    void process(AbstractVcs vcs, List<T> items);
  }

  public interface VcsSeparator<T> {
    AbstractVcs getVcsFor(T item);
  }

  public static <T> void processItemsByVcs(final Collection<T> items, final VcsSeparator<T> separator, PerVcsProcessor<T> processor) {
    final Map<AbstractVcs, List<T>> changesByVcs = new HashMap<AbstractVcs, List<T>>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (T item : items) {
          final AbstractVcs vcs = separator.getVcsFor(item);
          if (vcs != null) {
            List<T> vcsChanges = changesByVcs.get(vcs);
            if (vcsChanges == null) {
              vcsChanges = new ArrayList<T>();
              changesByVcs.put(vcs, vcsChanges);
            }
            vcsChanges.add(item);
          }
        }
      }
    });

    for (Map.Entry<AbstractVcs, List<T>> entry : changesByVcs.entrySet()) {
      processor.process(entry.getKey(), entry.getValue());
    }
  }

  public static void processChangesByVcs(final Project project, Collection<Change> changes, PerVcsProcessor<Change> processor) {
    processItemsByVcs(changes, new VcsSeparator<Change>() {
      @Override
      public AbstractVcs getVcsFor(final Change item) {
        return getVcsForChange(item, project);
      }
    }, processor);
  }

  public static void processVirtualFilesByVcs(final Project project, Collection<VirtualFile> files, PerVcsProcessor<VirtualFile> processor) {
    processItemsByVcs(files, new VcsSeparator<VirtualFile>() {
      @Override
      public AbstractVcs getVcsFor(final VirtualFile item) {
        return getVcsForFile(item, project);
      }
    }, processor);
  }

  public static void processFilePathsByVcs(final Project project, Collection<FilePath> files, PerVcsProcessor<FilePath> processor) {
    processItemsByVcs(files, new VcsSeparator<FilePath>() {
      @Override
      public AbstractVcs getVcsFor(final FilePath item) {
        return getVcsForFile(item.getIOFile(), project);
      }
    }, processor);
  }

  public static List<File> filePathsToFiles(Collection<FilePath> filePaths) {
    List<File> ioFiles = new ArrayList<File>();
    for(FilePath filePath: filePaths) {
      ioFiles.add(filePath.getIOFile());
    }
    return ioFiles;
  }

  public static boolean hasFileChanges(final Collection<Change> changes) {
    for(Change change: changes) {
      FilePath path = getFilePath(change);
      if (!path.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  public static void markInternalOperation(Iterable<Change> changes, boolean set) {
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      if (file != null) {
        file.putUserData(INTERNAL_OPERATION_KEY, set);
      }
    }
  }

  public static void markInternalOperation(VirtualFile file, boolean set) {
    file.putUserData(INTERNAL_OPERATION_KEY, set);
  }

  public static boolean isInternalOperation(VirtualFile file) {
    Boolean data = file.getUserData(INTERNAL_OPERATION_KEY);
    return data != null && data.booleanValue();   
  }

  public static String getDefaultChangeListName() {
    return VcsBundle.message("changes.default.changelist.name");
  }
}
