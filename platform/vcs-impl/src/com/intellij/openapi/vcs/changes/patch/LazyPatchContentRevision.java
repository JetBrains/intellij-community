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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LazyPatchContentRevision implements ContentRevision {
  private final VirtualFile myVf;
  private final FilePath myNewFilePath;
  private final String myRevision;
  private final TextFilePatch myPatch;

  private final AtomicNotNullLazyValue<Data> myData;

  public LazyPatchContentRevision(final VirtualFile vf, final FilePath newFilePath, final String revision, final TextFilePatch patch) {
    myVf = vf;
    myNewFilePath = newFilePath;
    myRevision = revision;
    myPatch = patch;

    myData = AtomicNotNullLazyValue.createValue(() -> loadContent());
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

  @Nullable
  public String getContent() {
    return myData.getValue().content;
  }

  public boolean isPatchApplyFailed() {
    return myData.getValue().patchApplyFailed;
  }

  @NotNull
  public FilePath getFile() {
    return myNewFilePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      public String asString() {
        return myRevision;
      }

      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }

  private static class Data {
    @Nullable public final String content;
    public final boolean patchApplyFailed;

    public Data(@Nullable String content, boolean patchApplyFailed) {
      this.content = content;
      this.patchApplyFailed = patchApplyFailed;
    }
  }
}
