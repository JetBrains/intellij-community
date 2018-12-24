// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.local.ChangeListCommand;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DelayedNotificator implements ChangeListListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.DelayedNotificator");

  @NotNull private final ChangeListManagerImpl myManager;
  @NotNull private final EventDispatcher<? extends ChangeListListener> myDispatcher;
  @NotNull private final ChangeListManagerImpl.Scheduler myScheduler;

  public DelayedNotificator(@NotNull ChangeListManagerImpl manager,
                            @NotNull EventDispatcher<? extends ChangeListListener> dispatcher,
                            @NotNull ChangeListManagerImpl.Scheduler scheduler) {
    myManager = manager;
    myDispatcher = dispatcher;
    myScheduler = scheduler;
  }

  public void callNotify(final ChangeListCommand command) {
    myScheduler.submit(() -> {
      try {
        command.doNotify(myDispatcher);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    });
  }


  @Override
  public void changeListAdded(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListAdded(list));
  }

  @Override
  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesRemoved(changes, fromList));
  }

  @Override
  public void changesAdded(final Collection<Change> changes, final ChangeList toList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesAdded(changes, toList));
  }

  @Override
  public void changeListRemoved(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListRemoved(list));
  }

  @Override
  public void changeListChanged(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListChanged(list));
  }

  @Override
  public void changeListRenamed(final ChangeList list, final String oldName) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListRenamed(list, oldName));
  }

  @Override
  public void changeListDataChanged(ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListDataChanged(list));
  }

  @Override
  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListCommentChanged(list, oldComment));
  }

  @Override
  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesMoved(changes, fromList, toList));
  }

  @Override
  public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
    defaultListChanged(oldDefaultList, newDefaultList, false);
  }

  @Override
  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList, boolean automatic) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().defaultListChanged(oldDefaultList, newDefaultList, automatic));
  }


  @Override
  public void unchangedFileStatusChanged() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().unchangedFileStatusChanged());
  }

  @Override
  public void changeListUpdateDone() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListUpdateDone());
  }

  @Override
  public void allChangeListsMappingsChanged() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().allChangeListsMappingsChanged());
  }

  public void changeListsForFileChanged(@NotNull FilePath path,
                                        @NotNull Set<String> removedChangeListsIds,
                                        @NotNull Set<String> addedChangeListsIds) {
    myScheduler.submit(() -> {
      Change change = myManager.getChange(path);
      if (change == null) return;
      List<Change> changes = Collections.singletonList(change);

      for (String listId : removedChangeListsIds) {
        LocalChangeList changeList = myManager.getChangeList(listId);
        if (changeList != null) {
          myDispatcher.getMulticaster().changesRemoved(changes, changeList);
        }
      }

      for (String listId : addedChangeListsIds) {
        LocalChangeList changeList = myManager.getChangeList(listId);
        if (changeList != null) {
          myDispatcher.getMulticaster().changesAdded(changes, changeList);
        }
      }
    });
  }
}