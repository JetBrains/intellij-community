// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public class SetDefault implements ChangeListCommand {
  private final String myNewDefaultName;
  private final boolean myAutomatic;

  private boolean myResult;
  private LocalChangeList myOldDefaultListCopy;
  private LocalChangeList myNewDefaultListCopy;

  public SetDefault(@NotNull String newDefaultName, boolean automatic) {
    myNewDefaultName = newDefaultName;
    myAutomatic = automatic;
  }

  @Override
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

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myResult) {
      dispatcher.getMulticaster().defaultListChanged(myOldDefaultListCopy, myNewDefaultListCopy, myAutomatic);
    }
  }
}
