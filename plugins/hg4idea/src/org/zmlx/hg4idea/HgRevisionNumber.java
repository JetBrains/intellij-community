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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collections;
import java.util.List;

public class HgRevisionNumber implements VcsRevisionNumber {

  private static final int SHORT_HASH_SIZE = 12;
  @NotNull private final String revision;
  @NotNull private final String changeset;
  @NotNull private final String commitMessage;
  @NotNull private final String author;
  @NotNull private final String email;
  @NotNull private final List<HgRevisionNumber> parents;
  @NotNull private final String mySubject;

  private final boolean isWorkingVersion;

  // this is needed in place of VcsRevisionNumber.NULL, because sometimes we need to return HgRevisionNumber.
  public static final HgRevisionNumber NULL_REVISION_NUMBER = new HgRevisionNumber("", "", "", "", Collections.<HgRevisionNumber>emptyList()) {
    @Override
    public int compareTo(VcsRevisionNumber o) {
      return NULL.compareTo(o);
    }

    @Override
    public String asString() {
      return NULL.asString();
    }
  };

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset,@NotNull  String author,@NotNull  String commitMessage) {
    return new HgRevisionNumber(revision, changeset, author, commitMessage, Collections.<HgRevisionNumber>emptyList());
  }

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset) {
    return new HgRevisionNumber(revision, changeset, "", "", Collections.<HgRevisionNumber>emptyList());
  }

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset,@NotNull  List<HgRevisionNumber> parents) {
    return new HgRevisionNumber(revision, changeset, "", "", parents);
  }

  public static HgRevisionNumber getLocalInstance(@NotNull String revision) {
    return new HgRevisionNumber(revision, "", "", "", Collections.<HgRevisionNumber>emptyList());
  }

  public HgRevisionNumber(@NotNull String revision,
                          @NotNull String changeset,
                          @NotNull String authorInfo,
                          @NotNull String commitMessage,
                          @NotNull List<HgRevisionNumber> parents) {
    this.commitMessage = commitMessage;
    Couple<String> authorArgs = HgUtil.parseUserNameAndEmail(authorInfo);
    this.author = authorArgs.getFirst();
    this.email = authorArgs.getSecond();
    this.parents = parents;
    this.revision = revision.trim();
    this.changeset = changeset.trim();
    isWorkingVersion = changeset.endsWith("+");
    mySubject = HgBaseLogParser.extractSubject(commitMessage);
  }

  @NotNull
  public String getChangeset() {
    return changeset;
  }

  @NotNull
  public String getRevision() {
    return revision;
  }

  public long getRevisionAsLong() {
    return java.lang.Long.parseLong(revision);
  }

  @NotNull
  public String getCommitMessage() {
    return commitMessage;
  }

  @NotNull
  public String getAuthor() {
    return author;
  }

  public boolean isWorkingVersion() {
    return isWorkingVersion;
  }

  public String asString() {
    if (revision.isEmpty()) {
      return changeset;
    }
    return revision + ":" + changeset;
  }

  @NotNull
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

    // One of the revisions is local. Local is "greater" than any from the history.
    if (changeset.isEmpty()) {
      return 1;
    }
    if (other.changeset.isEmpty()) {
      return -1;
    }

    // compare revision numbers.
    final int revCompare = java.lang.Long.valueOf(getRevisionNumber()).compareTo(java.lang.Long.valueOf(other.getRevisionNumber()));
    if (revCompare != 0) {
      return revCompare;
    }
    else if (getShortHash(changeset).equals(getShortHash(other.changeset))) {
      //if local revision numbers are equal then it's enough to compare 12 symbols hash; collisions couldn't occur
      return 0;
    }
    // If they are equal, the working revision is greater.
    if (isWorkingVersion) {
      return other.isWorkingVersion ? 0 : 1;
    } else {
      return other.isWorkingVersion ? -1 : 0;
    }
  }

  private static String getShortHash(@NotNull String changeset) {
    return changeset.substring(0, SHORT_HASH_SIZE);
  }

  /**
   * Returns the numeric part of the revision, i. e. the revision without trailing '+' if one exists.
   */
  public String getRevisionNumber() {
    if (isWorkingVersion) {
      return revision.substring(0, revision.length()-1);
    }
    return revision;
  }

  @Override
  public int hashCode() {
    // if short revision number is not empty, then short changeset is enough, a.e. annotations
    return Objects.hashCode(revision, revision.isEmpty() ? changeset : getShortHash(changeset));
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

  @NotNull
  public String getSubject() {
    return mySubject;
  }

  @NotNull
  public String getEmail() {
    return email;
  }
}
