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
import org.jetbrains.annotations.NotNull;

public class EditName implements ChangeListCommand {
  @NotNull private final String myFromName;
  @NotNull private final String myToName;

  private boolean myResult;
  private LocalChangeList myListCopy;

  public EditName(@NotNull String fromName, @NotNull String toName) {
    myFromName = fromName;
    myToName = toName;
  }

  public void apply(final ChangeListWorker worker) {
    myResult = worker.editName(myFromName, myToName);

    myListCopy = worker.getChangeListByName(myToName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myListCopy != null && myResult) {
      dispatcher.getMulticaster().changeListRenamed(myListCopy, myFromName);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
