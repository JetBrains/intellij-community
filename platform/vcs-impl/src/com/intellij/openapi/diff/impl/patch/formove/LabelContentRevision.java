/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.history.Label;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class LabelContentRevision implements ContentRevision {
  private final Label myLabel;
  private final FilePath myFilePath;
  private final FilePath myContentDetectorPath;

  private LabelContentRevision(@NotNull Label label, @NotNull FilePath filePath, @Nullable FilePath contentFilePath) {
    myLabel = label;
    myFilePath = filePath;
    myContentDetectorPath = contentFilePath != null ? contentFilePath : myFilePath;
  }

  @Nullable
  private byte[] getByteContent() {
    return myLabel.getByteContent(myContentDetectorPath.getPath()).getBytes();
  }

  @Nullable
  @Override
  public String getContent() throws VcsException {
    VirtualFile virtualFile = myContentDetectorPath.getVirtualFile();
    byte[] bytes = getByteContent();
    return bytes != null ? CharsetToolkit
      .decodeString(bytes, virtualFile != null ? virtualFile.getCharset() : CharsetToolkit.getDefaultSystemCharset()) : null;
  }

  @NotNull
  public FilePath getFile() {
    return myFilePath;
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }
}