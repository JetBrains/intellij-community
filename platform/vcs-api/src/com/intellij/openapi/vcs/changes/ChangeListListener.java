// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EventListener;

/**
 * @author max
 *
 * @see ChangeListManager#addChangeListListener(ChangeListListener)
 * @see ChangeListManager#removeChangeListListener(ChangeListListener)
 */
public interface ChangeListListener extends EventListener {
  Topic<ChangeListListener> TOPIC = Topic.create("VCS changelists changed", ChangeListListener.class);

  default void changeListAdded(ChangeList list) {}
  default void changeListRemoved(ChangeList list) {}
  default void changeListChanged(ChangeList list) {}
  default void changeListDataChanged(@NotNull ChangeList list) {}
  default void changeListRenamed(ChangeList list, @NlsSafe String oldName) {}
  default void changeListCommentChanged(ChangeList list, @NlsSafe String oldComment) {}
  default void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {}
  default void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList, boolean automatic) {
    defaultListChanged(oldDefaultList, newDefaultList);
  }

  default void changesAdded(Collection<? extends Change> changes, ChangeList toList) {}
  default void changesRemoved(Collection<? extends Change> changes, ChangeList fromList) {}
  default void changesMoved(Collection<? extends Change> changes, ChangeList fromList, ChangeList toList) {}
  default void allChangeListsMappingsChanged() {}


  default void changedFileStatusChanged() {}

  /**
   * Notifies that VCS finished updating added/deleted/modified files.
   *
   * @param upToDate true if no pending refreshes are scheduled
   */
  default void changedFileStatusChanged(boolean upToDate) {
    changedFileStatusChanged();
  }

  default void unchangedFileStatusChanged() {}

  /**
   * Notifies that VCS finished updating misc vcs-managed file statuses. Ex: ignored, unversioned, switched, etc.
   *
   * @param upToDate true if no pending refreshes are scheduled
   */
  default void unchangedFileStatusChanged(boolean upToDate) {
    unchangedFileStatusChanged();
  }

  /**
   * Combined event for {@link #changedFileStatusChanged()} and {@link #unchangedFileStatusChanged()}.
   */
  default void changeListUpdateDone() {}


  /**
   * @see ChangeListManager#areChangeListsEnabled
   */
  default void changeListAvailabilityChanged() {}
}
