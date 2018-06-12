/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ShelvedChange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelvedChange");

  private final String myPatchPath;
  private final String myBeforePath;
  private final String myAfterPath;
  private final FileStatus myFileStatus;
  private Change myChange;

  public ShelvedChange(final String patchPath, final String beforePath, final String afterPath, final FileStatus fileStatus) {
    myPatchPath = patchPath;
    myBeforePath = beforePath;
    // optimisation: memory
    myAfterPath = Comparing.equal(beforePath, afterPath) ? beforePath : afterPath;
    myFileStatus = fileStatus;
  }

  public boolean isConflictingChange(final Project project) {
    ContentRevision afterRevision = getChange(project).getAfterRevision();
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

  @Nullable
  public VirtualFile getBeforeVFUnderProject(final Project project) {
    if (myBeforePath == null || project.getBaseDir() == null) return null;
    final File baseDir = new File(project.getBaseDir().getPath());
    final File file = new File(baseDir, myBeforePath);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  public String getAfterPath() {
    return myAfterPath;
  }

  @Nullable
  public String getAfterFileName() {
    if (myAfterPath == null) return null;
    int pos = myAfterPath.lastIndexOf('/');
    if (pos >= 0) return myAfterPath.substring(pos+1);
    return myAfterPath;
  }

  public String getBeforeFileName() {
    int pos = myBeforePath.lastIndexOf('/');
    if (pos >= 0) return myBeforePath.substring(pos+1);
    return myBeforePath;
  }

  public String getBeforeDirectory() {
    int pos = myBeforePath.lastIndexOf('/');
    if (pos >= 0) return myBeforePath.substring(0, pos).replace('/', File.separatorChar);
    return File.separator;
  }

  public FileStatus getFileStatus() {
    return myFileStatus;
  }

  public Change getChange(Project project) {
    // todo unify with
    if (myChange == null) {
      File baseDir = new File(project.getBaseDir().getPath());

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
      myChange = new Change(beforeRevision, afterRevision, myFileStatus);
    }
    return myChange;
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
    for(TextFilePatch patch: filePatches) {
      if (myBeforePath.equals(patch.getBeforeName())) {
        return patch;
      }
    }
    return null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ShelvedChange)) return false;

    final ShelvedChange that = (ShelvedChange)o;

    if (myAfterPath != null ? !myAfterPath.equals(that.myAfterPath) : that.myAfterPath != null) return false;
    if (myBeforePath != null ? !myBeforePath.equals(that.myBeforePath) : that.myBeforePath != null) return false;
    if (myFileStatus != null ? !myFileStatus.equals(that.myFileStatus) : that.myFileStatus != null) return false;
    if (myPatchPath != null ? !myPatchPath.equals(that.myPatchPath) : that.myPatchPath != null) return false;

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

    public PatchedContentRevision(Project project, final FilePath beforeFilePath, final FilePath afterFilePath) {
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

  public String getPatchPath() {
    return myPatchPath;
  }

  public String toString() {
    return FileUtil.toSystemDependentName(myBeforePath == null ? myAfterPath : myBeforePath);
  }
}