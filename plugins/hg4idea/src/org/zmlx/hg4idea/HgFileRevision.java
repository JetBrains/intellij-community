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
package org.zmlx.hg4idea;

import com.google.common.base.Objects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class HgFileRevision implements VcsFileRevision {

  private final Project myProject;
  @NotNull private final HgFile myFile;
  @NotNull private final HgRevisionNumber myRevisionNumber;
  private final String myBranchName;
  private final Date myRevisionDate;
  private final String myAuthor;
  private final String myCommitMessage;
  private final Set<String> myFilesModified;
  private final Set<String> myFilesAdded;
  private final Set<String> myFilesDeleted;
  private Map<String,String> myFilesCopied;

  public HgFileRevision(Project project, @NotNull HgFile hgFile, @NotNull HgRevisionNumber vcsRevisionNumber,
                        String branchName, Date revisionDate, String author, String commitMessage,
                        Set<String> filesModified, Set<String> filesAdded, Set<String> filesDeleted, Map<String, String> filesCopied) {
    myProject = project;
    myFile = hgFile;
    myRevisionNumber = vcsRevisionNumber;
    myBranchName = branchName;
    myRevisionDate = revisionDate;
    myAuthor = author;
    myCommitMessage = commitMessage;
    myFilesModified = filesModified;
    myFilesAdded = filesAdded;
    myFilesDeleted = filesDeleted;
    myFilesCopied = filesCopied;
  }

  public HgRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  public String getBranchName() {
    return myBranchName;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  public Date getRevisionDate() {
    return myRevisionDate;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public String getCommitMessage() {
    return myCommitMessage;
  }

  public Set<String> getModifiedFiles() {
    return myFilesModified;
  }

  public Set<String> getAddedFiles() {
    return myFilesAdded;
  }

  public Set<String> getDeletedFiles() {
    return myFilesDeleted;
  }

  public Map<String, String> getCopiedFiles() {
    return myFilesCopied;
  }

  public byte[] loadContent() throws IOException, VcsException {
    try {
      Charset charset = myFile.toFilePath().getCharset();

      HgFile fileToCat = HgUtil.getFileNameInTargetRevision(myProject, myRevisionNumber, myFile);
      String result = new HgCatCommand(myProject).execute(fileToCat, myRevisionNumber, charset);
      if (result == null) {
        return new byte[0];
      } else {
        return result.getBytes(charset.name());
      }
    } catch (UnsupportedEncodingException e) {
      throw new VcsException(e);
    }
  }

  public byte[] getContent() throws IOException, VcsException {
    return loadContent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HgFileRevision revision = (HgFileRevision)o;

    if (!myFile.equals(revision.myFile)) {
      return false;
    }
    if (!myRevisionNumber.equals(revision.myRevisionNumber)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myFile, myRevisionNumber);
  }
}
