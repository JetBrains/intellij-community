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

public class SetDefault implements ChangeListCommand {
  private final String myNewDefaultName;
  private String myPrevious;
  private LocalChangeList myOldDefaultListCopy;
  private LocalChangeList myNewDefaultListCopy;

  public SetDefault(final String newDefaultName) {
    myNewDefaultName = newDefaultName;
  }

  public void apply(final ChangeListWorker worker) {
    myOldDefaultListCopy = worker.getDefaultListCopy();
    myPrevious = worker.setDefault(myNewDefaultName);
    myNewDefaultListCopy = worker.getDefaultListCopy();
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    dispatcher.getMulticaster().defaultListChanged(myOldDefaultListCopy, myNewDefaultListCopy);
  }

  public String getPrevious() {
    return myPrevious;
  }
}
