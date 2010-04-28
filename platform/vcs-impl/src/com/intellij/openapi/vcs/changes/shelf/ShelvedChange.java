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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 19:06:26
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
    myAfterPath = afterPath;
    myFileStatus = fileStatus;
  }

  public String getBeforePath() {
    return myBeforePath;
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
    if (myChange == null) {
      ContentRevision beforeRevision = null;
      ContentRevision afterRevision = null;
      File baseDir = new File(project.getBaseDir().getPath());

      File file = getAbsolutePath(baseDir, myBeforePath);
      final FilePathImpl beforePath = new FilePathImpl(file, false);
      beforePath.refresh();
      if (myFileStatus != FileStatus.ADDED) {
        beforeRevision = new CurrentContentRevision(beforePath) {
          @NotNull
          public VcsRevisionNumber getRevisionNumber() {
            return new TextRevisionNumber(VcsBundle.message("local.version.title"));
          }
        };
      }
      if (myFileStatus != FileStatus.DELETED) {
        final FilePathImpl afterPath = new FilePathImpl(getAbsolutePath(baseDir, myAfterPath), false);
        afterRevision = new PatchedContentRevision(beforePath, afterPath);
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
  public TextFilePatch loadFilePatch() throws IOException, PatchSyntaxException {
    List<TextFilePatch> filePatches = ShelveChangesManager.loadPatches(myPatchPath);
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
    int result = myPatchPath != null ? myPatchPath.hashCode() : 0;
    result = 31 * result + (myBeforePath != null ? myBeforePath.hashCode() : 0);
    result = 31 * result + (myAfterPath != null ? myAfterPath.hashCode() : 0);
    result = 31 * result + (myFileStatus != null ? myFileStatus.hashCode() : 0);
    return result;
  }

  private class PatchedContentRevision implements ContentRevision {
    private final FilePath myBeforeFilePath;
    private final FilePath myAfterFilePath;
    private String myContent;

    public PatchedContentRevision(final FilePath beforeFilePath, final FilePath afterFilePath) {
      myBeforeFilePath = beforeFilePath;
      myAfterFilePath = afterFilePath;
    }

    @Nullable
    public String getContent() throws VcsException {
      if (myContent == null) {
        try {
          myContent = loadContent();
        }
        catch (Exception e) {
          throw new VcsException(e);
        }
      }

      return myContent;
    }

    @Nullable
    private String loadContent() throws IOException, PatchSyntaxException, ApplyPatchException {
      TextFilePatch patch = loadFilePatch();
      if (patch != null) {
        return loadContent(patch);
      }
      return null;
    }

    private String loadContent(final TextFilePatch patch) throws ApplyPatchException {
      if (patch.isNewFile()) {
        return patch.getNewFileText();
      }
      if (patch.isDeletedFile()) {
        return null;
      }
      StringBuilder newText = new StringBuilder();
      ApplyFilePatchBase.applyModifications(patch, getBaseContent(), newText);
      return newText.toString();
    }

    private String getBaseContent() {
      myBeforeFilePath.refresh();
      final Document doc = FileDocumentManager.getInstance().getDocument(myBeforeFilePath.getVirtualFile());
      return doc.getText();
    }

    @NotNull
    public FilePath getFile() {
      return myAfterFilePath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return new TextRevisionNumber(VcsBundle.message("shelved.version.name"));
    }
  }

  public String getPatchPath() {
    return myPatchPath;
  }
}
