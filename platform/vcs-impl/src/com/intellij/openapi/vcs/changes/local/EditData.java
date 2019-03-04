// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListData;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditData implements ChangeListCommand {
  private final String myName;
  @Nullable private final ChangeListData myNewData;

  private boolean myResult;
  private LocalChangeList myListCopy;

  public EditData(@NotNull String name, @Nullable ChangeListData newData) {
    myName = name;
    myNewData = newData;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myResult = worker.editData(myName, myNewData);
    myListCopy = worker.getChangeListByName(myName);
  }

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myListCopy != null) {
      dispatcher.getMulticaster().changeListDataChanged(myListCopy);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
