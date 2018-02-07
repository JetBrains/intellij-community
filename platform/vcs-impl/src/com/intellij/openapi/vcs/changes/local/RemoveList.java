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
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class RemoveList implements ChangeListCommand {
  private final String myName;
  private boolean myRemoved;

  private LocalChangeList myListCopy;
  private LocalChangeList myDefaultListCopy;

  public RemoveList(final String name) {
    myName = name;
  }

  public void apply(final ChangeListWorker worker) {
    myListCopy = worker.getChangeListByName(myName);
    myDefaultListCopy = worker.getDefaultList();
    myRemoved = worker.removeChangeList(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myListCopy != null && myRemoved ) {
      ChangeListListener multicaster = dispatcher.getMulticaster();
      multicaster.changesMoved(myListCopy.getChanges(), myListCopy, myDefaultListCopy);
      multicaster.changeListRemoved(myListCopy);
    }
  }
}
