// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
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
      myNewDefaultListCopy = null;
      myResult = false;
      return;
    }

    String oldDefaultName = worker.setDefaultList(myNewDefaultName);
    myOldDefaultListCopy = worker.getChangeListByName(oldDefaultName);
    myNewDefaultListCopy = worker.getChangeListByName(myNewDefaultName);
    myResult = oldDefaultName != null;
  }

  @Override
  public void doNotify(final ChangeListListener listener) {
    if (myOldDefaultListCopy != null && myNewDefaultListCopy != null && myResult) {
      listener.defaultListChanged(myOldDefaultListCopy, myNewDefaultListCopy, myAutomatic);
    }
  }
}
