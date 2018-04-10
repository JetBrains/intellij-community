/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;

/**
 * @author max
 */
public interface CommittedChangeList extends ChangeList {
  String getCommitterName();
  Date getCommitDate();
  long getNumber();

  /**
   * Returns the branch on which this changelist occurred. This method may return null if
   * the changelist did not occur on a branch or if branching is not supported.
   *
   * @return the branch of this changelist, or null if not applicable.
   */
  @Nullable
  String getBranch();

  /**
   * Returns the VCS by which the changelist was generated. This method must return a not null
   * value for changelists returned by {@link com.intellij.openapi.vcs.CachingCommittedChangesProvider}.
   *
   * @return the VCS instance.
   */
  AbstractVcs getVcs();

  default Collection<Change> getChangesWithMovedTrees() {
    return getChanges();
  }

  /**
   * @return true if this change list can be modified, for example, by reverting some of the changes.
   */
  boolean isModifiable();

  void setDescription(final String newMessage);
}
