// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LazyPatchContentRevision implements ContentRevision {
  private final VirtualFile myVf;
  private final FilePath myNewFilePath;
  @NotNull private final String myRevision;
  private final TextFilePatch myPatch;

  private final NotNullLazyValue<Data> myData = NotNullLazyValue.atomicLazy(this::loadContent);

  public LazyPatchContentRevision(final VirtualFile vf,
                                  final FilePath newFilePath,
                                  @NotNull final String revision,
                                  final TextFilePatch patch) {
    myVf = vf;
    myNewFilePath = newFilePath;
    myRevision = revision;
    myPatch = patch;
  }

  private Data loadContent() {
    String localContext = ReadAction.compute(() -> {
      Document doc = FileDocumentManager.getInstance().getDocument(myVf);
      return doc == null ? null : doc.getText();
    });
    if (localContext == null) {
      return new Data(null, true);
    }

    GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(localContext, myPatch.getHunks());
    if (appliedPatch != null) {
      return new Data(appliedPatch.patchedText, false);
    }
    else {
      return new Data(null, true);
    }
  }

  @Override
  @Nullable
  public String getContent() {
    return myData.getValue().content;
  }

  public boolean isPatchApplyFailed() {
    return myData.getValue().patchApplyFailed;
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myNewFilePath;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      @NotNull
      @Override
      public String asString() {
        return myRevision;
      }

      @Override
      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }

  private static class Data {
    @Nullable public final String content;
    public final boolean patchApplyFailed;

    Data(@Nullable String content, boolean patchApplyFailed) {
      this.content = content;
      this.patchApplyFailed = patchApplyFailed;
    }
  }
}
