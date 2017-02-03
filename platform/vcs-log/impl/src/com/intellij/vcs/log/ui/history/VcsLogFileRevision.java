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
package com.intellij.vcs.log.ui.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class VcsLogFileRevision extends VcsFileRevisionEx {
  @NotNull private final Change myChange;
  @NotNull private final FilePath myPath;
  private final long myAuthorTime;
  @NotNull private final String myFullMessage;
  @Nullable private byte[] myContent = null;
  @NotNull private final VcsUser myAuthor;
  @NotNull private final VcsUser myCommitter;

  public VcsLogFileRevision(@NotNull VcsFullCommitDetails details, @NotNull Change change, @NotNull FilePath path) {
    myChange = change;
    myPath = path;

    myAuthor = details.getAuthor();
    myCommitter = details.getCommitter();
    myAuthorTime = details.getAuthorTime();
    myFullMessage = details.getFullMessage();
  }

  @Nullable
  @Override
  public String getAuthor() {
    return myAuthor.getName();
  }

  @Nullable
  @Override
  public String getAuthorEmail() {
    return myAuthor.getEmail();
  }

  @Nullable
  @Override
  public String getCommitterName() {
    return myCommitter.getName();
  }

  @Nullable
  @Override
  public String getCommitterEmail() {
    return myCommitter.getName();
  }

  @Nullable
  @Override
  public String getCommitMessage() {
    return myFullMessage;
  }

  @NotNull
  @Override
  public FilePath getPath() {
    return myPath;
  }

  @Nullable
  @Override
  public String getBranchName() {
    return null;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  @Override
  public byte[] loadContent() throws IOException, VcsException {
    if (myContent != null) return myContent;

    ContentRevision afterRevision = myChange.getAfterRevision();
    if (afterRevision != null) {
      String content = afterRevision.getContent();
      if (content != null) {
        myContent = content.getBytes(myPath.getCharset().name());
        return myContent;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public byte[] getContent() throws IOException, VcsException {
    return myContent;
  }

  @Override
  public VcsRevisionNumber getRevisionNumber() {
    if (myChange.getAfterRevision() == null) return null;
    return myChange.getAfterRevision().getRevisionNumber();
  }

  @Override
  public Date getRevisionDate() {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(myAuthorTime);
    return cal.getTime();
  }
}
