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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class CurrentContentRevision implements ContentRevision {
  protected FilePath myFile;

  public CurrentContentRevision(final FilePath file) {
    myFile = file;
  }

  @Nullable
  public String getContent() {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) {
      return null;
    }
    final Document doc = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
      public Document compute() {
        return FileDocumentManager.getInstance().getDocument(vFile);
    }});
    if (doc == null) return null;
    return doc.getText();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    final VirtualFile vFile = myFile.getVirtualFile();
    if (vFile == null || !vFile.isValid()) return null;
    return vFile;
  }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  @NotNull
  public static ContentRevision create(@NotNull FilePath file) {
    if (file.getFileType().isBinary()) {
      return new CurrentBinaryContentRevision(file);
    }
    return new CurrentContentRevision(file);
  }

  @NonNls
  public String toString() {
    return "CurrentContentRevision:" + myFile;
  }
}
