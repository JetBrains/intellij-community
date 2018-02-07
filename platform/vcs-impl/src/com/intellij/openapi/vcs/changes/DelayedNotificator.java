/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.local.ChangeListCommand;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class DelayedNotificator implements ChangeListListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.DelayedNotificator");

  private final EventDispatcher<ChangeListListener> myDispatcher;
  private final ChangeListManagerImpl.Scheduler myScheduler;

  public DelayedNotificator(@NotNull EventDispatcher<ChangeListListener> dispatcher,
                            @NotNull ChangeListManagerImpl.Scheduler scheduler) {
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


  public void changeListAdded(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListAdded(list));
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesRemoved(changes, fromList));
  }

  public void changesAdded(final Collection<Change> changes, final ChangeList toList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesAdded(changes, toList));
  }

  public void changeListRemoved(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListRemoved(list));
  }

  public void changeListChanged(final ChangeList list) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListChanged(list));
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListRenamed(list, oldName));
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListCommentChanged(list, oldComment));
  }

  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changesMoved(changes, fromList, toList));
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
    myScheduler.submit(() -> myDispatcher.getMulticaster().defaultListChanged(oldDefaultList, newDefaultList));
  }


  public void unchangedFileStatusChanged() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().unchangedFileStatusChanged());
  }

  public void changeListUpdateDone() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListUpdateDone());
  }

  public void changeListsChanged() {
    myScheduler.submit(() -> myDispatcher.getMulticaster().changeListsChanged());
  }
}