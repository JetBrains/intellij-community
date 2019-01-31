// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListData;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddList implements ChangeListCommand {
  @NotNull private final String myName;
  @Nullable private final String myComment;
  @Nullable private final ChangeListData myData;

  private boolean myWasListCreated;
  private LocalChangeList myNewListCopy;
  private String myOldComment;

  public AddList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data) {
    myName = name;
    myComment = comment;
    myData = data;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    LocalChangeList list = worker.getChangeListByName(myName);
    if (list == null) {
      // Create list with the same id, if we were invoked before (on "temp" worker during CLM update).
      String id = myNewListCopy != null ? myNewListCopy.getId() : null;

      myWasListCreated = true;
      myOldComment = null;
      myNewListCopy = worker.addChangeList(myName, myComment, id, myData);
    }
    else if (StringUtil.isNotEmpty(myComment)) {
      myWasListCreated = false;
      myOldComment = worker.editComment(myName, myComment);
      myNewListCopy = worker.getChangeListByName(myName);
    }
    else {
      myWasListCreated = false;
      myOldComment = null;
      myNewListCopy = list;
    }
  }

  @Override
  public void doNotify(final EventDispatcher<? extends ChangeListListener> dispatcher) {
    if (myWasListCreated) {
      dispatcher.getMulticaster().changeListAdded(myNewListCopy);
    }
    else if (myOldComment != null) {
      dispatcher.getMulticaster().changeListCommentChanged(myNewListCopy, myOldComment);
    }
  }

  @NotNull
  public LocalChangeList getNewListCopy() {
    return myNewListCopy;
  }
}
