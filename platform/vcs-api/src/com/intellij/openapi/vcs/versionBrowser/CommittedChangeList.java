// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;

public interface CommittedChangeList extends ChangeList {
  @NlsSafe
  String getCommitterName();
  Date getCommitDate();
  long getNumber();

  /**
   * Returns the branch on which this changelist occurred. This method may return null if
   * the changelist did not occur on a branch or if branching is not supported.
   *
   * @return the branch of this changelist, or null if not applicable.
   */
  @NlsSafe
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
