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
import org.jetbrains.annotations.Nullable;

public class SetDefault implements ChangeListCommand {
  private final String myNewDefaultName;

  private boolean myResult;
  private LocalChangeList myOldDefaultListCopy;
  private LocalChangeList myNewDefaultListCopy;

  public SetDefault(@Nullable String newDefaultName) {
    myNewDefaultName = newDefaultName;
  }

  public void apply(ChangeListWorker worker) {
    LocalChangeList list = worker.getChangeListByName(myNewDefaultName);
    if (list == null || list.isDefault()) {
      myOldDefaultListCopy = null;
      myResult = false;
      myNewDefaultListCopy = null;
      return;
    }

    myOldDefaultListCopy = worker.getDefaultList();
    myResult = worker.setDefaultList(myNewDefaultName);
    myNewDefaultListCopy = worker.getDefaultList();
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myResult) {
      dispatcher.getMulticaster().defaultListChanged(myOldDefaultListCopy, myNewDefaultListCopy);
    }
  }
}
