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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.Collections;
import java.util.Comparator;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommittedChangeListByDateComparator implements Comparator<CommittedChangeList> {

  public static final Comparator<CommittedChangeList> ASCENDING = new CommittedChangeListByDateComparator();
  public static final Comparator<CommittedChangeList> DESCENDING = Collections.reverseOrder(ASCENDING);

  protected CommittedChangeListByDateComparator() {
  }

  @Override
  public int compare(CommittedChangeList o1, CommittedChangeList o2) {
    return Comparing.compare(o1.getCommitDate(), o2.getCommitDate());
  }
}
