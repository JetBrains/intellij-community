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

public final class HgRevisionNumber implements VcsRevisionNumber {

  private final String revision;
  private final String changeset;
  private final boolean isWorkingVersion;

  public static HgRevisionNumber getInstance(String revision, String changeset) {
    return new HgRevisionNumber(revision, changeset);
  }

  public static HgRevisionNumber getLocalInstance(String revision) {
    return new HgRevisionNumber(revision, "");
  }

  private HgRevisionNumber(String revision, String changeset) {
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

  public int getRevisionAsInt() {
    return Integer.parseInt(revision);
  }

  public boolean isWorkingVersion() {
    return isWorkingVersion;
  }

  public String asString() {
    return revision + ":" + changeset;
  }

  public int compareTo(VcsRevisionNumber o) {
    if (this == o) {
      return 0;
    }

    if (!(o instanceof HgRevisionNumber)) {
      return -1;
    }

    HgRevisionNumber hgRevisionNumber = (HgRevisionNumber) o;
    if (changeset.equals(hgRevisionNumber.changeset)) {
      return 0;
    }

    return getRevisionAsInt() - hgRevisionNumber.getRevisionAsInt();
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

}
