// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;

class HgCurrentContentRevision implements ContentRevision {

  private final HgFile hgFile;
  private final VcsRevisionNumber revisionNumber;
  private final VirtualFile virtualFile;

  private FilePath filePath;

  public HgCurrentContentRevision(HgFile hgFile,
    VcsRevisionNumber revisionNumber, VirtualFile virtualFile) {
    this.hgFile = hgFile;
    this.revisionNumber = revisionNumber;
    this.virtualFile = virtualFile;
  }

  public String getContent() throws VcsException {
    Document doc = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
      public Document compute() {
        return FileDocumentManager.getInstance().getDocument(virtualFile);
      }
    });
    if (doc == null) {
      return null;
    }
    return doc.getText();
  }

  @NotNull
  public FilePath getFile() {
    if (filePath == null) {
      filePath = hgFile.toFilePath();
    }
    return filePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return revisionNumber;
  }
}
