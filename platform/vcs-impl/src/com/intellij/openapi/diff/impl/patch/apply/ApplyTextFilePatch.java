// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.CharsetEP;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ApplyTextFilePatch extends ApplyFilePatchBase<TextFilePatch> {
  public ApplyTextFilePatch(final TextFilePatch patch) {
    super(patch);
  }

  @Override
  protected @NotNull Result applyChange(@NotNull Project project,
                                        @NotNull VirtualFile fileToPatch,
                                        @NotNull FilePath pathBeforeRename,
                                        @Nullable Supplier<? extends CharSequence> baseContents) throws IOException {
    final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
    if (document == null) {
      throw new IOException("Failed to set contents for updated file " + fileToPatch.getPath());
    }

    GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(document.getText(), myPatch.getHunks());
    if (appliedPatch != null) {
      if (appliedPatch.status == ApplyPatchStatus.ALREADY_APPLIED) {
        return new Result(appliedPatch.status);
      }

      if (appliedPatch.status == ApplyPatchStatus.SUCCESS) {
        VcsFacade.getInstance().runHeavyModificationTask(project, document, () -> document.setText(appliedPatch.patchedText));
        FileDocumentManager.getInstance().saveDocument(document);
        return new Result(appliedPatch.status);
      }
    }

    ApplyPatchStatus status = appliedPatch != null ? appliedPatch.status : ApplyPatchStatus.FAILURE;
    assert status == ApplyPatchStatus.PARTIAL || status == ApplyPatchStatus.FAILURE;
    return new Result(status) {
      @Override
      public ApplyPatchForBaseRevisionTexts getMergeData() {
        return ApplyPatchForBaseRevisionTexts.create(project, fileToPatch, pathBeforeRename, myPatch,
                                                     baseContents != null ? baseContents.get() : null);
      }
    };
  }

  @Override
  protected void applyCreate(@NotNull Project project,
                             @NotNull VirtualFile newFile,
                             @Nullable CommitContext commitContext) throws IOException {
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
