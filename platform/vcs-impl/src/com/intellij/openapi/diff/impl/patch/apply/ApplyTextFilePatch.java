// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.CharsetEP;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public final class ApplyTextFilePatch extends ApplyFilePatchBase<TextFilePatch> {
  public ApplyTextFilePatch(final TextFilePatch patch) {
    super(patch);
  }

  @Override
  protected @NotNull Result applyChange(final Project project, final VirtualFile fileToPatch, final FilePath pathBeforeRename, @Nullable final Getter<? extends CharSequence> baseContents) throws IOException {
    final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
    if (document == null) {
      throw new IOException("Failed to set contents for updated file " + fileToPatch.getPath());
    }

    GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(document.getText(), myPatch.getHunks());
    if (appliedPatch != null) {
      VcsFacade.getInstance().runHeavyModificationTask(project, document, () -> document.setText(appliedPatch.patchedText));
      FileDocumentManager.getInstance().saveDocument(document);
      return new Result(appliedPatch.status);
    }
    return new Result(ApplyPatchStatus.FAILURE) {
      @Override
      public ApplyPatchForBaseRevisionTexts getMergeData() {
        return ApplyPatchForBaseRevisionTexts
          .create(project, fileToPatch, pathBeforeRename, myPatch, baseContents != null ? baseContents.get() : null);
      }
    };
  }

  @Override
  protected void applyCreate(Project project, @NotNull VirtualFile newFile, @Nullable CommitContext commitContext) throws IOException {
    Document document = FileDocumentManager.getInstance().getDocument(newFile);
    if (document == null) {
      throw new IOException("Failed to set contents for new file " + newFile.getPath());
    }

    String charsetName = commitContext == null ? null : CharsetEP.getCharset(newFile.getPath(), commitContext);
    if (charsetName != null) {
      try {
        newFile.setCharset(Charset.forName(charsetName));
      }
      catch (IllegalArgumentException ignore) {
      }
    }
    document.setText(myPatch.getSingleHunkPatchText());
    FileDocumentManager.getInstance().saveDocument(document);
  }
}
