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
package com.intellij.vcs;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.LongRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;

public class CommittedChangeListForRevision extends CommittedChangeListImpl implements VcsRevisionNumberAware {

  @NotNull private VcsRevisionNumber myRevisionNumber;

  public CommittedChangeListForRevision(@NotNull String subject,
                                        @NotNull String comment,
                                        @NotNull String committerName,
                                        @NotNull Date commitDate,
                                        @NotNull Collection<Change> changes,
                                        @NotNull VcsRevisionNumber revisionNumber) {
    super(subject, comment, committerName, getLong(revisionNumber), commitDate, changes);
    myRevisionNumber = revisionNumber;
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  private static long getLong(@NotNull VcsRevisionNumber number) {
    if (number instanceof LongRevisionNumber) return ((LongRevisionNumber)number).getLongRevisionNumber();
    return 0;
  }
}
