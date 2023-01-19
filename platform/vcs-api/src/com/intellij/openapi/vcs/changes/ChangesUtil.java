// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.hash;

public final class ChangesUtil {
  private static final Key<Boolean> INTERNAL_OPERATION_KEY = Key.create("internal vcs operation");

  public static final HashingStrategy<FilePath> CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable FilePath path) {
      return path != null ? hash(path.getPath(), path.isDirectory()) : 0;
    }

    @Override
    public boolean equals(@Nullable FilePath path1, @Nullable FilePath path2) {
      if (path1 == path2) return true;
      if (path1 == null || path2 == null) return false;

      return path1.isDirectory() == path2.isDirectory() && path1.getPath().equals(path2.getPath());
    }
  };

  public static final Comparator<LocalChangeList> CHANGELIST_COMPARATOR =
    Comparator.<LocalChangeList>comparingInt(list -> list.isDefault() ? -1 : 0)
      .thenComparing(list -> list.getName(), String::compareToIgnoreCase);

  private ChangesUtil() { }

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

  public static boolean isAffectedByChange(@NotNull FilePath filePath, @NotNull Change change) {
    if (filePath.equals(getBeforePath(change))) return true;
    if (filePath.equals(getAfterPath(change))) return true;
    return false;
  }

  public static @Nullable AbstractVcs getVcsForChange(@NotNull Change change, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(getFilePath(change));
  }

  public static @NotNull Set<AbstractVcs> getAffectedVcses(@NotNull Collection<? extends Change> changes, @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return ContainerUtil.map2SetNotNull(changes, change -> vcsManager.getVcsFor(getFilePath(change)));
  }

  @NotNull
  public static Set<AbstractVcs> getAffectedVcsesForFiles(@NotNull Collection<? extends VirtualFile> files, @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return ContainerUtil.map2SetNotNull(files, file -> vcsManager.getVcsFor(file));
  }

  @NotNull
  public static Set<AbstractVcs> getAffectedVcsesForFilePaths(@NotNull Collection<? extends FilePath> files, @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return ContainerUtil.map2SetNotNull(files, file -> vcsManager.getVcsFor(file));
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
  public static List<FilePath> getPaths(@NotNull Collection<? extends Change> changes) {
    return iteratePaths(changes).toList();
  }

  @NotNull
  public static List<File> getIoFilesFromChanges(@NotNull Collection<? extends Change> changes) {
    return iteratePaths(changes)
      .map(FilePath::getIOFile)
      .unique()
      .toList();
  }

  @NotNull
  public static JBIterable<FilePath> iteratePaths(@NotNull Iterable<? extends Change> changes) {
    return JBIterable.from(changes).flatMap(ChangesUtil::iteratePathsCaseSensitive);
  }

  @NotNull
  public static JBIterable<FilePath> iteratePathsCaseSensitive(@NotNull Change change) {
    FilePath beforePath = getBeforePath(change);
    FilePath afterPath = getAfterPath(change);

    if (CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(beforePath, afterPath)) {
      return JBIterable.of(beforePath);
    }
    else {
      return JBIterable.of(beforePath, afterPath).filterNotNull();
    }
  }

  @NotNull
  public static JBIterable<VirtualFile> iterateFiles(@NotNull Iterable<? extends Change> changes) {
    return iteratePaths(changes)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  @NotNull
  public static JBIterable<VirtualFile> iterateAfterRevisionsFiles(@NotNull Iterable<? extends Change> changes) {
    return JBIterable.from(changes)
      .map(ChangesUtil::getAfterPath)
      .filter(Objects::nonNull)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  public static VirtualFile @NotNull [] getFilesFromChanges(@NotNull Collection<? extends Change> changes) {
    return iterateFiles(changes).toArray(VirtualFile.EMPTY_ARRAY);
  }

  public static Navigatable @NotNull [] getNavigatableArray(@NotNull Project project, @NotNull Iterable<? extends VirtualFile> files) {
    return JBIterable.from(files)
      .filter(file -> !file.isDirectory())
      .map(file -> new OpenFileDescriptor(project, file))
      .toList()
      .toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
  }

  @Nullable
  public static LocalChangeList getChangeListIfOnlyOne(@NotNull Project project, Change @Nullable [] changes) {
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

  public static @Nullable @NlsSafe String getProjectRelativePath(@NotNull Project project, @Nullable File fileName) {
    if (fileName == null) {
      return null;
    }

    String baseDir = project.getBasePath();
    if (baseDir == null) {
      return fileName.toString();
    }

    String relativePath = FileUtil.getRelativePath(new File(baseDir), fileName);
    return relativePath == null ? fileName.toString() : relativePath;
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

  public static <T> void processItemsByVcs(@NotNull Collection<? extends T> items,
                                           @NotNull VcsSeparator<? super T> separator,
                                           @NotNull PerVcsProcessor<T> processor) {
    Map<AbstractVcs, List<T>> changesByVcs = new HashMap<>();
    ReadAction.run(() -> {
      for (T item : items) {
        AbstractVcs vcs = separator.getVcsFor(item);
        if (vcs != null) {
          changesByVcs.computeIfAbsent(vcs, __ -> new ArrayList<>()).add(item);
        }
      }
    });

    changesByVcs.forEach(processor::process);
  }

  public static void processChangesByVcs(@NotNull Project project,
                                         @NotNull Collection<? extends Change> changes,
                                         @NotNull PerVcsProcessor<Change> processor) {
    processItemsByVcs(changes, change -> getVcsForChange(change, project), processor);
  }

  public static void processVirtualFilesByVcs(@NotNull Project project,
                                              @NotNull Collection<? extends VirtualFile> files,
                                              @NotNull PerVcsProcessor<VirtualFile> processor) {
    if (files.isEmpty()) {
      return;
    }

    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    processItemsByVcs(files, file -> projectLevelVcsManager.getVcsFor(file), processor);
  }

  public static void processFilePathsByVcs(@NotNull Project project,
                                           @NotNull Collection<? extends FilePath> files,
                                           @NotNull PerVcsProcessor<FilePath> processor) {
    processItemsByVcs(files, filePath -> getVcsForFile(filePath.getIOFile(), project), processor);
  }

  @NotNull
  public static List<File> filePathsToFiles(@NotNull Collection<? extends FilePath> filePaths) {
    return ContainerUtil.map(filePaths, FilePath::getIOFile);
  }

  public static boolean hasFileChanges(@NotNull Collection<? extends Change> changes) {
    for (Change change : changes) {
      FilePath path = getFilePath(change);
      if (!path.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  public static void markInternalOperation(@NotNull Iterable<? extends Change> changes, boolean set) {
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      if (file != null) {
        markInternalOperation(file, set);
      }
    }
  }

  public static void markInternalOperation(@NotNull VirtualFile file, boolean set) {
    file.putUserData(INTERNAL_OPERATION_KEY, set ? Boolean.TRUE : null);
  }

  public static boolean isInternalOperation(@NotNull VirtualFile file) {
    return Boolean.TRUE.equals(file.getUserData(INTERNAL_OPERATION_KEY));
  }

  /**
   * Find common ancestor for changes (including both before and after files)
   */
  @Nullable
  public static File findCommonAncestor(@NotNull Collection<? extends Change> changes) {
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
           ? Objects.requireNonNull(after).getIOFile()
           : after == null ? before.getIOFile() : FileUtil.findAncestor(before.getIOFile(), after.getIOFile());
  }

  public static byte @NotNull [] loadContentRevision(@NotNull ContentRevision revision) throws VcsException {
    if (revision instanceof ByteBackedContentRevision) {
      byte[] bytes = ((ByteBackedContentRevision)revision).getContentAsBytes();
      if (bytes == null) throw new VcsException(VcsBundle.message("vcs.error.failed.to.load.file.content.from.vcs"));
      return bytes;
    }
    else {
      String content = revision.getContent();
      if (content == null) throw new VcsException(VcsBundle.message("vcs.error.failed.to.load.file.content.from.vcs"));
      return content.getBytes(revision.getFile().getCharset());
    }
  }

  public static boolean hasMeaningfulChangelists(@NotNull Project project) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (!changeListManager.areChangeListsEnabled()) return false;

    if (VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY) return true;

    List<LocalChangeList> changeLists = changeListManager.getChangeLists();
    if (changeLists.size() != 1) return true;
    if (!changeLists.get(0).isBlank()) return true;

    return false;
  }

  @NotNull
  private static Stream<FilePath> getPathsCaseSensitive(@NotNull Change change) {
    FilePath beforePath = getBeforePath(change);
    FilePath afterPath = getAfterPath(change);

    return Stream.of(beforePath, !CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(beforePath, afterPath) ? afterPath : null)
      .filter(Objects::nonNull);
  }

  /**
   * @deprecated Use {@link #iterateFiles(Iterable)}
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public static Stream<VirtualFile> getFiles(@NotNull Stream<? extends Change> changes) {
    return changes.flatMap(ChangesUtil::getPathsCaseSensitive)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  /**
   * @deprecated Use {@link #iterateAfterRevisionsFiles(Iterable)}
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public static Stream<VirtualFile> getAfterRevisionsFiles(@NotNull Stream<? extends Change> changes) {
    return changes
      .map(ChangesUtil::getAfterPath)
      .filter(Objects::nonNull)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  /**
   * @deprecated Use {@link #getNavigatableArray(Project, Iterable)}
   */
  @Deprecated(forRemoval = true)
  public static Navigatable @NotNull [] getNavigatableArray(@NotNull Project project, VirtualFile @NotNull [] files) {
    return getNavigatableArray(project, Stream.of(files));
  }

  /**
   * @deprecated Use {@link #getNavigatableArray(Project, Iterable)}
   */
  @Deprecated(forRemoval = true)
  public static Navigatable @NotNull [] getNavigatableArray(@NotNull Project project, @NotNull Stream<? extends VirtualFile> files) {
    return files
      .filter(file -> !file.isDirectory())
      .map(file -> new OpenFileDescriptor(project, file))
      .toArray(Navigatable[]::new);
  }
}
