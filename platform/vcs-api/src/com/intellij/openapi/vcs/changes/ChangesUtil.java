/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.newTroveSet;
import static java.util.Objects.hash;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * @author max
 */
public class ChangesUtil {
  private static final Key<Boolean> INTERNAL_OPERATION_KEY = Key.create("internal vcs operation");

  public static final TObjectHashingStrategy<FilePath> CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY = new TObjectHashingStrategy<FilePath>() {
    @Override
    public int computeHashCode(@Nullable FilePath path) {
      return path != null ? hash(path.getPath(), path.isDirectory()) : 0;
    }

    @Override
    public boolean equals(@Nullable FilePath path1, @Nullable FilePath path2) {
      if (path1 == path2) return true;
      if (path1 == null || path2 == null) return false;

      return path1.isDirectory() == path2.isDirectory() && path1.getPath().equals(path2.getPath());
    }
  };

  private ChangesUtil() {}

  @NotNull
  public static FilePath getFilePath(@NotNull Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) {
      revision = change.getBeforeRevision();
      assert revision != null;
    }

    return revision.getFile();
  }

  @Nullable
  public static FilePath getBeforePath(@NotNull Change change) {
    ContentRevision revision = change.getBeforeRevision();
    return revision == null ? null : revision.getFile();
  }

  @Nullable
  public static FilePath getAfterPath(@NotNull Change change) {
    ContentRevision revision = change.getAfterRevision();
    return revision == null ? null : revision.getFile();
  }

  @Nullable
  public static AbstractVcs getVcsForChange(@NotNull Change change, @NotNull Project project) {
    AbstractVcs result = ChangeListManager.getInstance(project).getVcsFor(change);

    return result != null ? result : ProjectLevelVcsManager.getInstance(project).getVcsFor(getFilePath(change));
  }

  @NotNull
  public static Set<AbstractVcs> getAffectedVcses(@NotNull Collection<Change> changes, @NotNull Project project) {
    return ContainerUtil.map2SetNotNull(changes, change -> getVcsForChange(change, project));
  }

  @Nullable
  public static AbstractVcs getVcsForFile(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
  }

  @Nullable
  public static AbstractVcs getVcsForFile(@NotNull File file, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(VcsUtil.getFilePath(file));
  }

  @NotNull
  public static List<FilePath> getPaths(@NotNull Collection<Change> changes) {
    THashSet<FilePath> distinctPaths = getAllPaths(changes.stream())
      .collect(toCollection(() -> newTroveSet(CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY)));
    return newArrayList(distinctPaths);
  }

  @NotNull
  public static List<File> getIoFilesFromChanges(@NotNull Collection<Change> changes) {
    return getAllPaths(changes.stream())
      .map(FilePath::getIOFile)
      .distinct()
      .collect(toList());
  }

  @NotNull
  public static Stream<FilePath> getAllPaths(@NotNull Stream<Change> changes) {
    return changes.flatMap(change ->
                             Stream.of(getBeforePath(change), getAfterPath(change))
                               .filter(Objects::nonNull)
                               .distinct());
  }

  @NotNull
  public static Stream<VirtualFile> getAllFiles(@NotNull Stream<Change> changes) {
    return getAllPaths(changes)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  /**
   * @deprecated Use {@link ChangesUtil#getAllFiles(Stream)}.
   */
  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  @Deprecated
  @NotNull
  public static VirtualFile[] getFilesFromChanges(@NotNull Collection<Change> changes) {
    return getAllFiles(changes.stream()).toArray(VirtualFile[]::new);
  }

  @NotNull
  public static Navigatable[] getNavigatableArray(@NotNull Project project, @NotNull VirtualFile[] files) {
    return getNavigatableArray(project, Stream.of(files));
  }

  @NotNull
  public static Navigatable[] getNavigatableArray(@NotNull Project project, @NotNull Stream<VirtualFile> files) {
    return files
      .filter(file -> !file.isDirectory())
      .map(file -> new OpenFileDescriptor(project, file))
      .toArray(Navigatable[]::new);
  }

  @Nullable
  public static ChangeList getChangeListIfOnlyOne(@NotNull Project project, @Nullable Change[] changes) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    String changeListName = manager.getChangeListNameIfOnlyOne(changes);

    return changeListName == null ? null : manager.findChangeList(changeListName);
  }

  public static FilePath getCommittedPath(@NotNull Project project, FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ChangeListManager.getInstance(project).getChange(filePath);
    if (change != null) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          afterRevision.getFile().equals(filePath)) {
        filePath = beforeRevision.getFile();
      }
    }
    return filePath;
  }

  public static FilePath getLocalPath(@NotNull Project project, FilePath filePath) {
    // check if the file has just been renamed (IDEADEV-15494)
    Change change = ReadAction.compute(() -> {
      if (project.isDisposed()) throw new ProcessCanceledException();
      return ChangeListManager.getInstance(project).getChange(filePath);
    });
    if (change != null) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          beforeRevision.getFile().equals(filePath)) {
        return afterRevision.getFile();
      }
    }
    return filePath;
  }

  @Nullable
  public static VirtualFile findValidParentUnderReadAction(@NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    return file != null ? file : getValidParentUnderReadAction(path);
  }

  @Nullable
  public static VirtualFile findValidParentAccurately(@NotNull FilePath filePath) {
    VirtualFile result = filePath.getVirtualFile();

    if (result == null && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      result = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
    }
    if (result == null) {
      result = getValidParentUnderReadAction(filePath);
    }

    return result;
  }

  @Nullable
  private static VirtualFile getValidParentUnderReadAction(@NotNull FilePath filePath) {
    return ReadAction.compute(() -> {
      VirtualFile result = null;
      FilePath parent = filePath;
      LocalFileSystem lfs = LocalFileSystem.getInstance();

      while (result == null && parent != null) {
        result = lfs.findFileByPath(parent.getPath());
        parent = parent.getParentPath();
      }

      return result;
    });
  }

  @Nullable
  public static String getProjectRelativePath(@NotNull Project project, @Nullable File fileName) {
    if (fileName == null) return null;
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) return fileName.toString();
    String relativePath = FileUtil.getRelativePath(VfsUtilCore.virtualToIoFile(baseDir), fileName);
    if (relativePath != null) return relativePath;
    return fileName.toString();
  }

  public static boolean isBinaryContentRevision(@Nullable ContentRevision revision) {
    return revision instanceof BinaryContentRevision && !revision.getFile().isDirectory();
  }

  public static boolean isBinaryChange(@NotNull Change change) {
    return isBinaryContentRevision(change.getBeforeRevision()) || isBinaryContentRevision(change.getAfterRevision());
  }

  public static boolean isTextConflictingChange(@NotNull Change change) {
    FileStatus status = change.getFileStatus();
    return FileStatus.MERGED_WITH_CONFLICTS.equals(status) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status);
  }

  @FunctionalInterface
  public interface PerVcsProcessor<T> {
    void process(@NotNull AbstractVcs vcs, @NotNull List<T> items);
  }

  @FunctionalInterface
  public interface VcsSeparator<T> {
    @Nullable
    AbstractVcs getVcsFor(@NotNull T item);
  }

  public static <T> void processItemsByVcs(@NotNull Collection<T> items,
                                           @NotNull VcsSeparator<T> separator,
                                           @NotNull PerVcsProcessor<T> processor) {
    Map<AbstractVcs, List<T>> changesByVcs = ReadAction.compute(
      () -> StreamEx.of(items)
        .mapToEntry(separator::getVcsFor, identity())
        .nonNullKeys()
        .grouping()
    );

    changesByVcs.forEach(processor::process);
  }

  public static void processChangesByVcs(@NotNull Project project,
                                         @NotNull Collection<Change> changes,
                                         @NotNull PerVcsProcessor<Change> processor) {
    processItemsByVcs(changes, change -> getVcsForChange(change, project), processor);
  }

  public static void processVirtualFilesByVcs(@NotNull Project project,
                                              @NotNull Collection<VirtualFile> files,
                                              @NotNull PerVcsProcessor<VirtualFile> processor) {
    processItemsByVcs(files, file -> getVcsForFile(file, project), processor);
  }

  public static void processFilePathsByVcs(@NotNull Project project,
                                           @NotNull Collection<FilePath> files,
                                           @NotNull PerVcsProcessor<FilePath> processor) {
    processItemsByVcs(files, filePath -> getVcsForFile(filePath.getIOFile(), project), processor);
  }

  @NotNull
  public static List<File> filePathsToFiles(@NotNull Collection<FilePath> filePaths) {
    return filePaths.stream()
      .map(FilePath::getIOFile)
      .collect(toList());
  }

  public static boolean hasFileChanges(@NotNull Collection<Change> changes) {
    return changes.stream()
      .map(ChangesUtil::getFilePath)
      .anyMatch(path -> !path.isDirectory());
  }

  public static void markInternalOperation(@NotNull Iterable<Change> changes, boolean set) {
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      if (file != null) {
        markInternalOperation(file, set);
      }
    }
  }

  public static void markInternalOperation(@NotNull VirtualFile file, boolean set) {
    file.putUserData(INTERNAL_OPERATION_KEY, set);
  }

  public static boolean isInternalOperation(@NotNull VirtualFile file) {
    return Boolean.TRUE.equals(file.getUserData(INTERNAL_OPERATION_KEY));
  }

  /**
   * Find common ancestor for changes (included both before and after files)
   */
  @Nullable
  public static File findCommonAncestor(@NotNull Collection<Change> changes) {
    File ancestor = null;
    for (Change change : changes) {
      File currentChangeAncestor = getCommonBeforeAfterAncestor(change);
      if (currentChangeAncestor == null) return null;
      if (ancestor == null) {
        ancestor = currentChangeAncestor;
      }
      else {
        ancestor = FileUtil.findAncestor(ancestor, currentChangeAncestor);
        if (ancestor == null) return null;
      }
    }
    return ancestor;
  }

  @Nullable
  private static File getCommonBeforeAfterAncestor(@NotNull Change change) {
    FilePath before = getBeforePath(change);
    FilePath after = getAfterPath(change);
    return before == null
           ? ObjectUtils.assertNotNull(after).getIOFile()
           : after == null ? before.getIOFile() : FileUtil.findAncestor(before.getIOFile(), after.getIOFile());
  }
}
