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

  private String branchName;
  private Date revisionDate;
  private String author;
  private String commitMessage;
  private Set<String> filesModified;
  private Set<String> filesAdded;
  private Map<String, String> filesCopied;
  private Set<String> filesDeleted;
  private byte[] content;

  public HgFileRevision(Project project, HgFile hgFile, HgRevisionNumber vcsRevisionNumber) {
    this.project = project;
    this.hgFile = hgFile;
    this.vcsRevisionNumber = vcsRevisionNumber;
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

  public void setBranchName(String branchName) {
    this.branchName = branchName;
  }

  public void setRevisionDate(Date revisionDate) {
    this.revisionDate = revisionDate;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public void setCommitMessage(String commitMessage) {
    this.commitMessage = commitMessage;
  }

  public Set<String> getFilesModified() {
    return filesModified;
  }

  public void setFilesModified(Set<String> filesModified) {
    this.filesModified = filesModified;
  }

  public Set<String> getFilesAdded() {
    return filesAdded;
  }

  public void setFilesAdded(Set<String> filesAdded) {
    this.filesAdded = filesAdded;
  }

  public Map<String, String> getFilesCopied() {
    return filesCopied;
  }

  public void setFilesCopied(Map<String, String> filesCopied) {
    this.filesCopied = filesCopied;
  }

  public Set<String> getFilesDeleted() {
    return filesDeleted;
  }

  public void setFilesDeleted(Set<String> filesDeleted) {
    this.filesDeleted = filesDeleted;
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
