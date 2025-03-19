// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListData;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class EditData implements ChangeListCommand {
  private final String myName;
  private final @Nullable ChangeListData myNewData;

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
  public void doNotify(final ChangeListListener listener) {
    if (myListCopy != null) {
      listener.changeListDataChanged(myListCopy);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
