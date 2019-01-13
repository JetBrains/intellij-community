// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public void apply(final ChangeListWorker worker) {
    myResult = worker.editName(myFromName, myToName);

    myListCopy = worker.getChangeListByName(myToName);
  }

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myListCopy != null && myResult) {
      dispatcher.getMulticaster().changeListRenamed(myListCopy, myFromName);
    }
  }

  public boolean isResult() {
    return myResult;
  }
}
