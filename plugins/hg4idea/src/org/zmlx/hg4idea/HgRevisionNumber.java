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

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;

public final class HgRevisionNumber implements VcsRevisionNumber {

  private final String revision;
  private final String changeset;
  private final String commitMessage;
  private final String author;
  private final List<HgRevisionNumber> parents;

  private final boolean isWorkingVersion;

  public static HgRevisionNumber getInstance(String revision, String changeset, String author, String commitMessage) {
    return new HgRevisionNumber(revision, changeset, author, commitMessage, Collections.<HgRevisionNumber>emptyList());
  }

  public static HgRevisionNumber getInstance(String revision, String changeset) {
    return new HgRevisionNumber(revision, changeset, "", "", Collections.<HgRevisionNumber>emptyList());
  }
  
  public static HgRevisionNumber getInstance(String revision, String changeset, List<HgRevisionNumber> parents) {
    return new HgRevisionNumber(revision, changeset, "", "", parents);
  }

  public static HgRevisionNumber getLocalInstance(String revision) {
    return new HgRevisionNumber(revision, "", "", "", Collections.<HgRevisionNumber>emptyList());
  }

  private HgRevisionNumber(String revision, String changeset, String author, String commitMessage, List<HgRevisionNumber> parents) {
    this.commitMessage = commitMessage;
    this.author = author;
    this.parents = parents;
    this.revision = revision.trim();
    this.changeset = changeset.trim();
    isWorkingVersion = changeset.endsWith("+");
  }

  public String getChangeset() {
    return changeset;
  }

  public String getRevision() {
    return revision;
  }

  public long getRevisionAsLong() {
    return java.lang.Long.parseLong(revision);
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public String getAuthor() {
    return author;
  }

  public boolean isWorkingVersion() {
    return isWorkingVersion;
  }

  public String asString() {
    return revision + ":" + changeset;
  }

  public List<HgRevisionNumber> getParents() {
    return parents;
  }

  public int compareTo(VcsRevisionNumber o) {
    // boundary cases
    if (this == o) {
      return 0;
    }
    if (!(o instanceof HgRevisionNumber)) {
      return -1;
    }
    final HgRevisionNumber other = (HgRevisionNumber) o;
    if (changeset.equals(other.changeset)) {
      return 0;
    }

    // compare revision numbers.
    final int revCompare = java.lang.Long.valueOf(getRevisionNumber()).compareTo(java.lang.Long.valueOf(other.getRevisionNumber()));
    if (revCompare != 0) {
      return revCompare;
    }
    // If they are equal, the working revision is greater.
    if (isWorkingVersion) {
      return other.isWorkingVersion ? 0 : 1;
    } else {
      return other.isWorkingVersion ? -1 : 0;
    }
  }

  /**
   * Returns the numeric part of the revision, i. e. the revision without trailing '+' if one exists. 
   */
  private String getRevisionNumber() {
    if (isWorkingVersion) {
      return revision.substring(0, revision.length()-1);
    }
    return revision;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
      .append(revision)
      .append(changeset)
      .toHashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgRevisionNumber)) {
      return false;
    }
    HgRevisionNumber that = (HgRevisionNumber) object;
    return compareTo(that) == 0;
  }

  @Override
  public String toString() {
    return asString();
  }
}
