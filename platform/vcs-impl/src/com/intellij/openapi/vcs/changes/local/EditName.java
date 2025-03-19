// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EditName implements ChangeListCommand {
  private final @NotNull String myFromName;
  private final @NotNull String myToName;

  private boolean myResult;
  private LocalChangeList myListCopy;

  public EditName(@NotNull String fromName, @NotNull String toName) {
    myFromName = fromName;
    myToName = toName;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myResult = worker.editName(myFromName, myToName);

    myListCopy = worker.getChangeListByName(myToName);
  }

  @Override
  public void doNotify(final ChangeListListener listener) {
    if (myListCopy != null && myResult) {
      listener.changeListRenamed(myListCopy, myFromName);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
