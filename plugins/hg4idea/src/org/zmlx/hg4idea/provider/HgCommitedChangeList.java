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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.zmlx.hg4idea.HgRevisionNumber;

import java.util.Collection;
import java.util.Date;

final class HgCommitedChangeList extends CommittedChangeListImpl {

  private HgRevisionNumber revisionNumber;

  public HgCommitedChangeList(String comment, String committerName,
    HgRevisionNumber revisionNumber, Date date, Collection<Change> commitDate) {
    super(buildDisplayName(revisionNumber, comment),
      comment, committerName, revisionNumber.getRevisionAsInt(), date, commitDate);
    this.revisionNumber = revisionNumber;
  }

  public HgRevisionNumber getRevisionNumber() {
    return revisionNumber;
  }

  private static String buildDisplayName(HgRevisionNumber number, String message) {
    return number.asString() + " " + message;
  }

}
