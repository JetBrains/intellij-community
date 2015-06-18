/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.CharsetEP;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public class ApplyTextFilePatch extends ApplyFilePatchBase<TextFilePatch> {
  public ApplyTextFilePatch(final TextFilePatch patch) {
    super(patch);
  }

  @Nullable
  protected Result applyChange(final Project project, final VirtualFile fileToPatch, final FilePath pathBeforeRename, final Getter<CharSequence> baseContents) throws IOException {
    byte[] fileContents = fileToPatch.contentsToByteArray();
    CharSequence text = LoadTextUtil.getTextByBinaryPresentation(fileContents, fileToPatch);
    final GenericPatchApplier applier = new GenericPatchApplier(text, myPatch.getHunks());
    if (applier.execute()) {
      final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
      if (document == null) {
        throw new IOException("Failed to set contents for updated file " + fileToPatch.getPath());
      }
      document.setText(applier.getAfter());
      FileDocumentManager.getInstance().saveDocument(document);
      return new Result(applier.getStatus()) {
        @Override
        public ApplyPatchForBaseRevisionTexts getMergeData() {
          return null;
        }
      };
    }
    applier.trySolveSomehow();
    return new Result(ApplyPatchStatus.FAILURE) {
      @Override
      public ApplyPatchForBaseRevisionTexts getMergeData() {
        return ApplyPatchForBaseRevisionTexts.create(project, fileToPatch, pathBeforeRename, myPatch, baseContents);
      }
    };
  }

  protected void applyCreate(Project project, final VirtualFile newFile, CommitContext commitContext) throws IOException {
    final Document document = FileDocumentManager.getInstance().getDocument(newFile);
    if (document == null) {
      throw new IOException("Failed to set contents for new file " + newFile.getPath());
    }
    final String charsetName = CharsetEP.getCharset(newFile.getPath(), commitContext);
    if (charsetName != null) {
      try {
        final Charset charset = Charset.forName(charsetName);
        newFile.setCharset(charset);
      } catch (IllegalArgumentException e) {
        //
      }
    }
    document.setText(myPatch.getNewFileText());
    FileDocumentManager.getInstance().saveDocument(document);
  }
}
