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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
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
  private final Map<String, String> myFilesMoved; // actually we collect moved and track copied as added

  public HgFileRevision(Project project, @NotNull HgFile hgFile, @NotNull HgRevisionNumber vcsRevisionNumber,
                        String branchName, Date revisionDate, String author, String commitMessage,
                        Set<String> filesModified, Set<String> filesAdded, Set<String> filesDeleted, Map<String, String> filesMoved) {
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
    myFilesMoved = filesMoved;
  }

  @NotNull
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

  @Nullable
  public String getAuthor() {
    return myAuthor;
  }

  @Nullable
  public String getCommitMessage() {
    return myCommitMessage;
  }

  @NotNull
  public Set<String> getModifiedFiles() {
    return myFilesModified;
  }

  @NotNull
  public Set<String> getAddedFiles() {
    return myFilesAdded;
  }

  @NotNull
  public Set<String> getDeletedFiles() {
    return myFilesDeleted;
  }

  @NotNull
  public Map<String, String> getMovedFiles() {
    return myFilesMoved;
  }

  @NotNull
  public byte[] loadContent() {
    final HgFile fileToCat = HgUtil.getFileNameInTargetRevision(myProject, myRevisionNumber, myFile);
    return HgUtil.loadContent(myProject, myRevisionNumber, fileToCat);
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
    return Objects.hash(myFile, myRevisionNumber);
  }
}