// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class VcsLogFileRevision extends VcsFileRevisionEx {
  private final @NotNull ContentRevision myRevision;
  private final @NotNull FilePath myPath;
  private final @NotNull VcsUser myAuthor;
  private final @NotNull VcsUser myCommitter;
  private final long myAuthorTime;
  private final long myCommitTime;
  private final @NotNull String myFullMessage;
  private final boolean myIsDeleted;

  private byte @Nullable [] myContent = null;

  public VcsLogFileRevision(@NotNull VcsCommitMetadata commitMetadata,
                            @NotNull ContentRevision revision,
                            @NotNull FilePath path, boolean isDeleted) {
    myRevision = revision;
    myPath = path;

    myAuthor = commitMetadata.getAuthor();
    myCommitter = commitMetadata.getCommitter();
    myAuthorTime = commitMetadata.getAuthorTime();
    myCommitTime = commitMetadata.getCommitTime();
    myFullMessage = commitMetadata.getFullMessage();
    myIsDeleted = isDeleted;
  }

  @Override
  public @Nullable String getAuthor() {
    return myAuthor.getName();
  }

  @Override
  public @Nullable String getAuthorEmail() {
    return myAuthor.getEmail();
  }

  @Override
  public @Nullable String getCommitterName() {
    return myCommitter.getName();
  }

  @Override
  public @Nullable String getCommitterEmail() {
    return myCommitter.getName();
  }

  @Override
  public @Nullable String getCommitMessage() {
    return myFullMessage;
  }

  @Override
  public @NotNull FilePath getPath() {
    return myPath;
  }

  @Override
  public @Nullable String getBranchName() {
    return null;
  }

  @Override
  public @Nullable RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  @Override
  public byte[] loadContent() throws IOException, VcsException {
    if (myContent == null) {
      if (myRevision instanceof ByteBackedContentRevision) {
        myContent = ((ByteBackedContentRevision)myRevision).getContentAsBytes();
      }
      else {
        String content = myRevision.getContent();
        if (content != null) {
          myContent = content.getBytes(myPath.getCharset());
        }
      }
    }

    return myContent;
  }

  @Override
  public byte @Nullable [] getContent() {
    return myContent;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myRevision.getRevisionNumber();
  }

  @Override
  public Date getRevisionDate() {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(myCommitTime);
    return cal.getTime();
  }

  @Override
  public @Nullable Date getAuthorDate() {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(myAuthorTime);
    return cal.getTime();
  }

  @Override
  public boolean isDeleted() {
    return myIsDeleted;
  }
}
