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

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MoveChanges implements ChangeListCommand {
  private final String myName;
  private final List<Change> myChanges;

  private MultiMap<LocalChangeList, Change> myMovedFrom;
  private LocalChangeList myListCopy;

  public MoveChanges(@NotNull String name, @NotNull Change[] changes) {
    myName = name;
    myChanges = ContainerUtil.skipNulls(Arrays.asList(changes));
  }

  public void apply(final ChangeListWorker worker) {
    myMovedFrom = worker.moveChangesTo(myName, myChanges);

    myListCopy = worker.getChangeListByName(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myMovedFrom != null && myListCopy != null) {
      for (LocalChangeList fromList : myMovedFrom.keySet()) {
        Collection<Change> changesInList = myMovedFrom.get(fromList);
        dispatcher.getMulticaster().changesMoved(changesInList, fromList, myListCopy);
      }
    }
  }
}
