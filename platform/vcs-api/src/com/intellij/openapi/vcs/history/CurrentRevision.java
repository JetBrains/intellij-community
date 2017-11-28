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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

public class CurrentRevision implements VcsFileRevision {
  private static final Logger LOG = Logger.getInstance(CurrentRevision.class);

  private final VirtualFile myFile;
  public static final String CURRENT = VcsBundle.message("vcs.revision.name.current");
  private final VcsRevisionNumber myRevisionNumber;

  public CurrentRevision(@NotNull VirtualFile file, @NotNull VcsRevisionNumber revision) {
    myFile = file;
    myRevisionNumber = revision;
  }

  public String getCommitMessage() {
    return "[" + CURRENT + "]";
  }

  public byte[] loadContent() throws IOException, VcsException {
    return getContent();
  }

  public Date getRevisionDate() {
    return new Date(myFile.getTimeStamp());
  }

  public byte[] getContent() {
    try {
      Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(myFile));
      if (document != null) {
        return document.getText().getBytes(myFile.getCharset().name());
      }
      else {
        return myFile.contentsToByteArray();
      }
    }
    catch (final IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  public String getAuthor() {
    return "";
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  public String getBranchName() {
    return null;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;  // use initial url..
  }
}
