// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
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
import com.intellij.openapi.vcs.changes.*;
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

  @NotNull private final Path myPatchPath;
  @NotNull private final String myBeforePath;
  @NotNull private final String myAfterPath;
  @NotNull private final FileStatus myFileStatus;
  @NotNull private final Change myChange;

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
    if (afterRevision == null) return false;
    try {
      afterRevision.getContent();
    }
    catch (VcsException e) {
      if (e.getCause() instanceof ApplyPatchException) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public String getBeforePath() {
    return myBeforePath;
  }

  @NotNull
  public String getAfterPath() {
    return myAfterPath;
  }

  @NotNull
  public FileStatus getFileStatus() {
    return myFileStatus;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  /**
   * @deprecated Parameter unused, use {@link #getChange()}
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public Change getChange(@NotNull Project project) {
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
        @NotNull
        public VcsRevisionNumber getRevisionNumber() {
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

  @Nullable
  private static TextFilePatch loadFilePatch(@NotNull Project project,
                                             @NotNull Path patchPath,
                                             @NotNull String beforePath,
                                             @Nullable CommitContext commitContext)
    throws IOException, PatchSyntaxException {
    List<TextFilePatch> filePatches = ShelveChangesManager.loadPatches(project, patchPath, commitContext);
    return ContainerUtil.find(filePatches, patch -> beforePath.equals(patch.getBeforeName()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ShelvedChange)) return false;

    final ShelvedChange that = (ShelvedChange)o;

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
    @NotNull private final Project myProject;
    @NotNull private final Path myPatchPath;
    @NotNull private final String myBeforePath;
    @NotNull private final FilePath myBeforeFilePath;
    @NotNull private final FilePath myAfterFilePath;

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
    @Nullable
    public String getContent() throws VcsException {
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

    @Nullable
    private String loadContent() throws IOException, PatchSyntaxException, VcsException {
      TextFilePatch patch = loadFilePatch(myProject, myPatchPath, myBeforePath, null);
      if (patch != null) {
        return loadContent(patch);
      }
      return null;
    }

    private String loadContent(final TextFilePatch patch) throws VcsException {
      if (patch.isNewFile()) {
        return patch.getSingleHunkPatchText();
      }
      if (patch.isDeletedFile()) {
        return null;
      }
      GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(getBaseContent(), patch.getHunks());
      if (appliedPatch != null) {
        return appliedPatch.patchedText;
      }
      throw new VcsException(VcsBundle.message("patch.apply.error.conflict"));
    }

    @NotNull
    private String getBaseContent() throws VcsException {
      return ReadAction.compute(() -> {
        VirtualFile file = myBeforeFilePath.getVirtualFile();
        if (file == null) throw new VcsException(VcsBundle.message("patch.apply.error.file.not.found", myBeforeFilePath));
        final Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null) throw new VcsException(VcsBundle.message("patch.apply.error.document.not.found", file));
        return doc.getText();
      });
    }

    @Override
    @NotNull
    public FilePath getFile() {
      return myAfterFilePath;
    }

    @Override
    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return new TextRevisionNumber(VcsBundle.message("shelved.version.name"));
    }
  }

  public @NotNull Path getPatchPath() {
    return myPatchPath;
  }

  public String toString() {
    return FileUtil.toSystemDependentName(myBeforePath);
  }
}