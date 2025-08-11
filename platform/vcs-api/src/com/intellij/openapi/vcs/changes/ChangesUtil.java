// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.*;

public final class ChangesUtil {
  private static final Key<Boolean> INTERNAL_OPERATION_KEY = Key.create("internal vcs operation");

  public static final HashingStrategy<FilePath> CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY = com.intellij.platform.vcs.changes.ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY;

  public static final Comparator<LocalChangeList> CHANGELIST_COMPARATOR =
    Comparator.<LocalChangeList>comparingInt(list -> list.isDefault() ? -1 : 0)
      .thenComparing(list -> list.getName(), String::compareToIgnoreCase);

  private ChangesUtil() { }

  public static @NotNull FilePath getFilePath(@NotNull Change change) {
    return com.intellij.platform.vcs.changes.ChangesUtil.getFilePath(change);
  }

  public static @Nullable FilePath getBeforePath(@NotNull Change change) {
    return com.intellij.platform.vcs.changes.ChangesUtil.getBeforePath(change);
  }

  public static @Nullable FilePath getAfterPath(@NotNull Change change) {
    return com.intellij.platform.vcs.changes.ChangesUtil.getAfterPath(change);
  }

  public static @Nullable AbstractVcs getVcsForChange(@NotNull Change change, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(getFilePath(change));
  }

  public static @Unmodifiable @NotNull Set<AbstractVcs> getAffectedVcses(@NotNull Collection<? extends Change> changes, @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager.getAllActiveVcss().length == 1) {
      for (Change change : changes) {
        AbstractVcs vcs = vcsManager.getVcsFor(getFilePath(change));
        if (vcs != null) return Collections.singleton(vcs);
      }
      return Collections.emptySet();
    }

    return ContainerUtil.map2SetNotNull(changes, change -> vcsManager.getVcsFor(getFilePath(change)));
  }

  public static @Unmodifiable @NotNull Set<AbstractVcs> getAffectedVcsesForFilePaths(@NotNull Collection<? extends FilePath> files,
                                                                                     @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager.getAllActiveVcss().length == 1) {
      for (FilePath file : files) {
        AbstractVcs vcs = vcsManager.getVcsFor(file);
        if (vcs != null) return Collections.singleton(vcs);
      }
      return Collections.emptySet();
    }

    return ContainerUtil.map2SetNotNull(files, file -> vcsManager.getVcsFor(file));
  }

  public static @Nullable AbstractVcs getVcsForFile(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
  }

  /**
   * @deprecated This method will detect {@link FilePath#isDirectory()} using NIO.
   * Avoid using the method, if {@code isDirectory} is known from context or not important.
   */
  @Deprecated(forRemoval = true)
  public static @Nullable AbstractVcs getVcsForFile(@NotNull File file, @NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(VcsUtil.getFilePath(file));
  }

  public static @Unmodifiable @NotNull List<FilePath> getPaths(@NotNull Collection<? extends Change> changes) {
    return iteratePaths(changes).toList();
  }

  public static @Unmodifiable @NotNull List<File> getIoFilesFromChanges(@NotNull Collection<? extends Change> changes) {
    return iteratePaths(changes)
      .map(FilePath::getIOFile)
      .unique()
      .toList();
  }

  public static @NotNull JBIterable<FilePath> iteratePaths(@NotNull Iterable<? extends Change> changes) {
    return JBIterable.from(changes).flatMap(com.intellij.platform.vcs.changes.ChangesUtil::iteratePathsCaseSensitive);
  }

  public static boolean equalsCaseSensitive(@Nullable FilePath path1, @Nullable FilePath path2) {
    return com.intellij.platform.vcs.changes.ChangesUtil.equalsCaseSensitive(path1, path2);
  }

  public static @NotNull JBIterable<VirtualFile> iterateFiles(@NotNull Iterable<? extends Change> changes) {
    return iteratePaths(changes)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull);
  }

  public static @NotNull JBIterable<VirtualFile> iterateAfterRevisionFiles(@NotNull Iterable<? extends Change> changes) {
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
    return com.intellij.platform.vcs.changes.ChangesUtil.getNavigatableArray(project, files);
  }

  public static @Nullable LocalChangeList getChangeListIfOnlyOne(@NotNull Project project, Change @Nullable [] changes) {
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

  /**
   * @deprecated Prefer using {@link com.intellij.vcsUtil.VcsImplUtil#findValidParentAccurately(FilePath)}
   */
  @Deprecated
  public static @Nullable VirtualFile findValidParentAccurately(@NotNull FilePath filePath) {
    VirtualFile result = filePath.getVirtualFile();

    if (result == null && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      result = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.getPath());
    }
    if (result == null) {
      result = getValidParentUnderReadAction(filePath);
    }

    return result;
  }

  /**
   * @deprecated Prefer using {@link NewVirtualFileSystem#findCachedFileByPath(NewVirtualFileSystem, String)}
   */
  @Deprecated
  private static @Nullable VirtualFile getValidParentUnderReadAction(@NotNull FilePath filePath) {
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
    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    processItemsByVcs(files, filePath -> projectLevelVcsManager.getVcsFor(filePath), processor);
  }

  public static @Unmodifiable @NotNull List<File> filePathsToFiles(@NotNull Collection<? extends FilePath> filePaths) {
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
  public static @Nullable File findCommonAncestor(@NotNull Collection<? extends Change> changes) {
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

  private static @Nullable File getCommonBeforeAfterAncestor(@NotNull Change change) {
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
    if (!changeListManager.areChangeListsEnabled()) {
      return false;
    }

    if (VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY) {
      return true;
    }

    List<LocalChangeList> changeLists = changeListManager.getChangeLists();
    return changeLists.size() != 1 || !changeLists.get(0).isBlank();
  }
}
