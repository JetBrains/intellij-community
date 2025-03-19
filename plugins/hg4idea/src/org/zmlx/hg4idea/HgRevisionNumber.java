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

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HgRevisionNumber implements VcsRevisionNumber {

  private static final int SHORT_HASH_SIZE = 12;
  private final @NotNull String revision;
  private final @NotNull String changeset;
  private final @NotNull String commitMessage;
  private final @NotNull String author;
  private final @NotNull String email;
  private final @NotNull List<? extends HgRevisionNumber> parents;
  private final @NotNull String mySubject;

  private final boolean isWorkingVersion;

  // this is needed in place of VcsRevisionNumber.NULL, because sometimes we need to return HgRevisionNumber.
  public static final HgRevisionNumber NULL_REVISION_NUMBER = new HgRevisionNumber("", "", "", "", Collections.emptyList()) {
    @Override
    public int compareTo(VcsRevisionNumber o) {
      return NULL.compareTo(o);
    }

    @Override
    public @NotNull String asString() {
      return NULL.asString();
    }
  };

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset,@NotNull  String author,@NotNull  String commitMessage) {
    return new HgRevisionNumber(revision, changeset, author, commitMessage, Collections.emptyList());
  }

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset) {
    return new HgRevisionNumber(revision, changeset, "", "", Collections.emptyList());
  }

  public static HgRevisionNumber getInstance(@NotNull String revision,@NotNull  String changeset,@NotNull List<? extends HgRevisionNumber> parents) {
    return new HgRevisionNumber(revision, changeset, "", "", parents);
  }

  public static HgRevisionNumber getLocalInstance(@NotNull String revision) {
    return new HgRevisionNumber(revision, "", "", "", Collections.emptyList());
  }

  public HgRevisionNumber(@NotNull String revision,
                          @NotNull String changeset,
                          @NotNull String authorInfo,
                          @NotNull String commitMessage,
                          @NotNull List<? extends HgRevisionNumber> parents) {
    this(revision, changeset, HgUtil.parseUserNameAndEmail(authorInfo).getFirst(), HgUtil.parseUserNameAndEmail(authorInfo).getSecond(),
         commitMessage, parents);
  }

  public HgRevisionNumber(@NotNull String revision,
                          @NotNull String changeset,
                          @NotNull String author,
                          @NotNull String email,
                          @NotNull String commitMessage,
                          @NotNull List<? extends HgRevisionNumber> parents) {
    this.commitMessage = commitMessage;
    this.author = author;
    this.email = email;
    this.parents = parents;
    this.revision = revision.trim();
    this.changeset = changeset.trim();
    isWorkingVersion = changeset.endsWith("+");
    mySubject = HgBaseLogParser.extractSubject(commitMessage);
  }

  public @NlsSafe @NotNull String getChangeset() {
    return changeset;
  }

  public @NlsSafe @NotNull String getRevision() {
    return revision;
  }

  public long getRevisionAsLong() {
    return java.lang.Long.parseLong(revision);
  }

  public @NlsSafe @NotNull String getCommitMessage() {
    return commitMessage;
  }

  public @NlsSafe @NotNull String getName() {
    return author;
  }

  public @NlsSafe @NotNull String getEmail() {
    return email;
  }

  public @NlsSafe @NotNull String getAuthor() {
    return VcsUserUtil.getUserName(author, email);
  }

  public boolean isWorkingVersion() {
    return isWorkingVersion;
  }

  @Override
  public @NotNull String asString() {
    if (revision.isEmpty()) {
      return changeset;
    }
    return revision + ":" + changeset;
  }

  public @NotNull List<? extends HgRevisionNumber> getParents() {
    return parents;
  }

  @Override
  public int compareTo(VcsRevisionNumber o) {
    // boundary cases
    if (this == o) {
      return 0;
    }
    if (!(o instanceof HgRevisionNumber other)) {
      return -1;
    }
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
    return Objects.hash(revision, revision.isEmpty() ? changeset : getShortHash(changeset));
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgRevisionNumber that)) {
      return false;
    }
    return compareTo(that) == 0;
  }

  @Override
  public String toString() {
    return asString();
  }

  public @NotNull String getSubject() {
    return mySubject;
  }
}