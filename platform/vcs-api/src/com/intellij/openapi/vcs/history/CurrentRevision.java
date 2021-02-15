// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

public class CurrentRevision implements VcsFileRevision {
  private static final Logger LOG = Logger.getInstance(CurrentRevision.class);

  private final VirtualFile myFile;
  private final VcsRevisionNumber myRevisionNumber;

  public CurrentRevision(@NotNull VirtualFile file, @NotNull VcsRevisionNumber revision) {
    myFile = file;
    myRevisionNumber = revision;
  }

  @Override
  public String getCommitMessage() {
    return "[" + getCurrent() + "]";
  }

  @Override
  public byte[] getContent() {
    return loadContent();
  }

  @Override
  public Date getRevisionDate() {
    return new Date(myFile.getTimeStamp());
  }

  @Override
  public byte[] loadContent() {
    try {
      Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(myFile));
      if (document != null) {
        return document.getText().getBytes(myFile.getCharset());
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

  @Override
  public String getAuthor() {
    return "";
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public String getBranchName() {
    return null;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;  // use initial url..
  }

  public static @Nls String getCurrent() {
    return VcsBundle.message("vcs.revision.name.current");
  }
}
