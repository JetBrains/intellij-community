// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class SetReadOnly implements ChangeListCommand {
  private final String myName;
  private final boolean myValue;

  private boolean myResult;
  private LocalChangeList myListCopy;

  public SetReadOnly(@NotNull String name, boolean value) {
    myName = name;
    myValue = value;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myResult = worker.setReadOnly(myName, myValue);

    myListCopy = worker.getChangeListByName(myName);
  }

  @Override
  public void doNotify(final ChangeListListener listener) {
    if (myListCopy != null && myResult) {
      listener.changeListChanged(myListCopy);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
