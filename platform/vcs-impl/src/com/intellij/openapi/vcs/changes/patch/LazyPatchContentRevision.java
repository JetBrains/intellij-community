// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class LazyPatchContentRevision implements ContentRevision {
  private final VirtualFile myVf;
  private final FilePath myNewFilePath;
  private final @NotNull String myRevision;
  private final TextFilePatch myPatch;

  private final Supplier<Data> myData = new SynchronizedClearableLazy<>(this::loadContent);

  public LazyPatchContentRevision(final VirtualFile vf,
                                  final FilePath newFilePath,
                                  final @NotNull String revision,
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
  public @Nullable String getContent() {
    return myData.get().content;
  }

  public boolean isPatchApplyFailed() {
    return myData.get().patchApplyFailed;
  }

  @Override
  public @NotNull FilePath getFile() {
    return myNewFilePath;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      @Override
      public @NotNull String asString() {
        return myRevision;
      }

      @Override
      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }

  private static class Data {
    public final @Nullable String content;
    public final boolean patchApplyFailed;

    Data(@Nullable String content, boolean patchApplyFailed) {
      this.content = content;
      this.patchApplyFailed = patchApplyFailed;
    }
  }
}
