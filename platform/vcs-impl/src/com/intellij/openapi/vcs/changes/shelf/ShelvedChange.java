// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ShelvedChange {
  private static final Logger LOG = Logger.getInstance(ShelvedChange.class);

  private final @NotNull Path myPatchPath;
  private final @NotNull String myBeforePath;
  private final @NotNull String myAfterPath;
  private final @NotNull FileStatus myFileStatus;
  private final @NotNull Change myChange;

  private ShelvedChange(@NotNull Path patchPath,
                        @NotNull String beforePath,
                        @NotNull String afterPath,
                        @NotNull FileStatus fileStatus,
                        @NotNull Change change) {
    myPatchPath = patchPath;
    myBeforePath = beforePath;
    myAfterPath = afterPath;
    myFileStatus = fileStatus;
    myChange = change;
  }

  public static ShelvedChange create(@NotNull Project project,
                                     @NotNull Path patchPath,
                                     @NotNull String beforePath,
                                     @NotNull String afterPath,
                                     @NotNull FileStatus fileStatus) {
    // optimisation: memory
    afterPath = Objects.equals(beforePath, afterPath) ? beforePath : afterPath;
    Change change = createChange(project, patchPath, beforePath, afterPath, fileStatus);
    return new ShelvedChange(patchPath, beforePath, afterPath, fileStatus, change);
  }

  public static ShelvedChange copyToNewPatch(@NotNull Project project,
                                             @NotNull Path newPatchPath,
                                             @NotNull ShelvedChange shelvedChange) {
    return create(project, newPatchPath, shelvedChange.getBeforePath(), shelvedChange.getAfterPath(), shelvedChange.getFileStatus());
  }

  public boolean isConflictingChange() {
    ContentRevision afterRevision = getChange().getAfterRevision();
    if (afterRevision instanceof PatchedContentRevision patchedRevision) {
      return patchedRevision.isConflictingChange();
    }
    return false;
  }

  public @NotNull String getBeforePath() {
    return myBeforePath;
  }

  public @NotNull String getAfterPath() {
    return myAfterPath;
  }

  public @NotNull FileStatus getFileStatus() {
    return myFileStatus;
  }

  public @NotNull Change getChange() {
    return myChange;
  }

  /**
   * @deprecated Parameter unused, use {@link #getChange()}
   */
  @Deprecated(forRemoval = true)
  public @NotNull Change getChange(@NotNull Project project) {
    return myChange;
  }

  private static Change createChange(@NotNull Project project,
                                     @NotNull Path patchPath,
                                     @NotNull String beforePath,
                                     @NotNull String afterPath,
                                     @NotNull FileStatus fileStatus) {
    File baseDir = new File(Objects.requireNonNull(project.getBasePath()));

    FilePath beforeFilePath = VcsUtil.getFilePath(getAbsolutePath(baseDir, beforePath), false);
    FilePath afterFilePath = VcsUtil.getFilePath(getAbsolutePath(baseDir, afterPath), false);

    ContentRevision beforeRevision = null;
    if (fileStatus != FileStatus.ADDED) {
      beforeRevision = new CurrentContentRevision(beforeFilePath) {
        @Override
        public @NotNull VcsRevisionNumber getRevisionNumber() {
          return new TextRevisionNumber(VcsBundle.message("local.version.title"));
        }
      };
    }
    ContentRevision afterRevision = null;
    if (fileStatus != FileStatus.DELETED) {
      afterRevision = new PatchedContentRevision(project, patchPath, beforePath, beforeFilePath, afterFilePath);
    }
    return new Change(beforeRevision, afterRevision, fileStatus);
  }

  private static File getAbsolutePath(@NotNull File baseDir, @NotNull String relativePath) {
    File file;
    try {
      file = new File(baseDir, relativePath).getCanonicalFile();
    }
    catch (IOException e) {
      LOG.info(e);
      file = new File(baseDir, relativePath);
    }
    return file;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ShelvedChange that)) return false;

    if (!Objects.equals(myAfterPath, that.myAfterPath)) return false;
    if (!Objects.equals(myBeforePath, that.myBeforePath)) return false;
    if (!Objects.equals(myFileStatus, that.myFileStatus)) return false;
    if (!Objects.equals(myPatchPath, that.myPatchPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myPatchPath, myBeforePath, myAfterPath, myFileStatus);
  }

  private static class PatchedContentRevision implements ContentRevision {
    private final @NotNull Project myProject;
    private final @NotNull Path myPatchPath;
    private final @NotNull String myBeforePath;
    private final @NotNull FilePath myBeforeFilePath;
    private final @NotNull FilePath myAfterFilePath;

    PatchedContentRevision(@NotNull Project project,
                           @NotNull Path patchPath,
                           @NotNull String beforePath,
                           @NotNull FilePath beforeFilePath,
                           @NotNull FilePath afterFilePath) {
      myProject = project;
      myPatchPath = patchPath;
      myBeforePath = beforePath;
      myBeforeFilePath = beforeFilePath;
      myAfterFilePath = afterFilePath;
    }

    @Override
    public @Nullable String getContent() throws VcsException {
      try {
        // content based on local shouldn't be cached because local file may be changed (during show diff also)
        return loadContent();
      }
      catch (VcsException e) {
        throw e;
      }
      catch (Exception e) {
        throw new VcsException(e);
      }
    }

    private @Nullable String loadContent() throws IOException, PatchSyntaxException, VcsException {
      TextFilePatch patch = loadFilePatch();
      if (patch == null) return null;

      if (patch.isNewFile()) {
        return patch.getSingleHunkPatchText();
      }
      if (patch.isDeletedFile()) {
        return null;
      }

      String localContent = loadLocalContent();
      GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(localContent, patch.getHunks());
      if (appliedPatch != null) {
        return appliedPatch.patchedText;
      }
      throw new VcsException(VcsBundle.message("patch.apply.error.conflict"));
    }

    public boolean isConflictingChange() {
      try {
        TextFilePatch patch = loadFilePatch();
        if (patch == null) return false;
        if (patch.isNewFile() || patch.isDeletedFile()) return false;

        String localContent = loadLocalContent();
        GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(localContent, patch.getHunks());
        return appliedPatch == null;
      }
      catch (IOException | PatchSyntaxException | VcsException ignore) {
        return false;
      }
    }

    private @Nullable TextFilePatch loadFilePatch() throws IOException, PatchSyntaxException {
      List<TextFilePatch> filePatches = ShelveChangesManager.loadPatches(myProject, myPatchPath, null);
      return ContainerUtil.find(filePatches, filePatch -> myBeforePath.equals(filePatch.getBeforeName()));
    }

    private @NotNull String loadLocalContent() throws VcsException {
      return ReadAction.compute(() -> {
        VirtualFile file = myBeforeFilePath.getVirtualFile();
        if (file == null) throw new VcsException(VcsBundle.message("patch.apply.error.file.not.found", myBeforeFilePath));
        final Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null) throw new VcsException(VcsBundle.message("patch.apply.error.document.not.found", file));
        return doc.getText();
      });
    }

    @Override
    public @NotNull FilePath getFile() {
      return myAfterFilePath;
    }

    @Override
    public @NotNull VcsRevisionNumber getRevisionNumber() {
      return new TextRevisionNumber(VcsBundle.message("shelved.version.name"));
    }
  }

  public @NotNull Path getPatchPath() {
    return myPatchPath;
  }

  @Override
  public String toString() {
    return FileUtil.toSystemDependentName(myBeforePath);
  }
}