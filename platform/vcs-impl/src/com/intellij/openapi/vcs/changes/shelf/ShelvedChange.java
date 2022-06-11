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

  private final Path myPatchPath;
  private final String myBeforePath;
  private final String myAfterPath;
  private final FileStatus myFileStatus;
  @NotNull private final Change myChange;

  public ShelvedChange(@NotNull Project project,
                       @NotNull Path patchPath,
                       String beforePath,
                       String afterPath,
                       FileStatus fileStatus) {
    myPatchPath = patchPath;
    myBeforePath = beforePath;
    // optimisation: memory
    myAfterPath = Objects.equals(beforePath, afterPath) ? beforePath : afterPath;
    myFileStatus = fileStatus;
    myChange = createChange(project);
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

  public String getBeforePath() {
    return myBeforePath;
  }

  public String getAfterPath() {
    return myAfterPath;
  }

  public FileStatus getFileStatus() {
    return myFileStatus;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  @NotNull
  @Deprecated(forRemoval = true)
  public Change getChange(@NotNull Project project) {
    return myChange;
  }

  private Change createChange(@NotNull Project project) {
    File baseDir = new File(Objects.requireNonNull(project.getBasePath()));

    File file = getAbsolutePath(baseDir, myBeforePath);
    FilePath beforePath = VcsUtil.getFilePath(file, false);
    ContentRevision beforeRevision = null;
    if (myFileStatus != FileStatus.ADDED) {
      beforeRevision = new CurrentContentRevision(beforePath) {
        @Override
        @NotNull
        public VcsRevisionNumber getRevisionNumber() {
          return new TextRevisionNumber(VcsBundle.message("local.version.title"));
        }
      };
    }
    ContentRevision afterRevision = null;
    if (myFileStatus != FileStatus.DELETED) {
      FilePath afterPath = VcsUtil.getFilePath(getAbsolutePath(baseDir, myAfterPath), false);
      afterRevision = new PatchedContentRevision(project, beforePath, afterPath);
    }
    return new Change(beforeRevision, afterRevision, myFileStatus);
  }

  private static File getAbsolutePath(final File baseDir, final String relativePath) {
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
  public TextFilePatch loadFilePatch(final Project project, CommitContext commitContext) throws IOException, PatchSyntaxException {
    List<TextFilePatch> filePatches = ShelveChangesManager.loadPatches(project, myPatchPath, commitContext);
    return ContainerUtil.find(filePatches, patch -> myBeforePath.equals(patch.getBeforeName()));
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

  private class PatchedContentRevision implements ContentRevision {
    private final Project myProject;
    private final FilePath myBeforeFilePath;
    private final FilePath myAfterFilePath;

    PatchedContentRevision(Project project, final FilePath beforeFilePath, final FilePath afterFilePath) {
      myProject = project;
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
      catch (Exception e) {
        throw new VcsException(e);
      }
    }

    @Nullable
    private String loadContent() throws IOException, PatchSyntaxException, ApplyPatchException {
      TextFilePatch patch = loadFilePatch(myProject, null);
      if (patch != null) {
        return loadContent(patch);
      }
      return null;
    }

    private String loadContent(final TextFilePatch patch) throws ApplyPatchException {
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
      throw new ApplyPatchException("Apply patch conflict");
    }

    private String getBaseContent() {
      return ReadAction.compute(() -> {
        final Document doc = FileDocumentManager.getInstance().getDocument(myBeforeFilePath.getVirtualFile());
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
    return FileUtil.toSystemDependentName(myBeforePath == null ? myAfterPath : myBeforePath);
  }
}