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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.zmlx.hg4idea.command.HgCatCommand;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class HgFileRevision implements VcsFileRevision {

  private final Project project;
  private final HgFile hgFile;
  private final HgRevisionNumber vcsRevisionNumber;
  private final String branchName;
  private final Date revisionDate;
  private final String author;
  private final String commitMessage;
  private final Set<String> filesModified;
  private final Set<String> filesAdded;
  private final Set<String> filesDeleted;
  private byte[] content;
  private Map<String,String> filesCopied;

  public HgFileRevision(Project project, HgFile hgFile, HgRevisionNumber vcsRevisionNumber,
    String branchName, Date revisionDate, String author, String commitMessage,
    Set<String> filesModified, Set<String> filesAdded, Set<String> filesDeleted, Map<String, String> filesCopied) {
    this.project = project;
    this.hgFile = hgFile;
    this.vcsRevisionNumber = vcsRevisionNumber;
    this.branchName = branchName;
    this.revisionDate = revisionDate;
    this.author = author;
    this.commitMessage = commitMessage;
    this.filesModified = filesModified;
    this.filesAdded = filesAdded;
    this.filesDeleted = filesDeleted;
    this.filesCopied = filesCopied;
  }

  public HgRevisionNumber getRevisionNumber() {
    return vcsRevisionNumber;
  }

  public String getBranchName() {
    return branchName;
  }

  public Date getRevisionDate() {
    return revisionDate;
  }

  public String getAuthor() {
    return author;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public Set<String> getModifiedFiles() {
    return filesModified;
  }

  public Set<String> getAddedFiles() {
    return filesAdded;
  }

  public Set<String> getDeletedFiles() {
    return filesDeleted;
  }

  public Map<String, String> getCopiedFiles() {
    return filesCopied;
  }

  public void loadContent() throws VcsException {
    try {
      Charset charset = hgFile.toFilePath().getCharset();
      String result = new HgCatCommand(project).execute(hgFile, vcsRevisionNumber, charset);
      if (result == null) {
        content = new byte[0];
      } else {
        content = result.getBytes(charset.name());
      }
    } catch (UnsupportedEncodingException e) {
      throw new VcsException(e);
    }
  }

  public byte[] getContent() throws IOException {
    return content;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
      .append(hgFile)
      .append(vcsRevisionNumber)
      .toHashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgFileRevision)) {
      return false;
    }
    HgFileRevision that = (HgFileRevision) object;
    return new EqualsBuilder()
      .append(hgFile, that.hgFile)
      .append(vcsRevisionNumber, that.vcsRevisionNumber)
      .isEquals();
  }

}
