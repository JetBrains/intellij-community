// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class EditComment implements ChangeListCommand {
  private final String myName;
  private final String myNewComment;

  private String myOldComment;
  private LocalChangeList myListCopy;

  public EditComment(@NotNull String name, @NotNull String newComment) {
    myNewComment = newComment;
    myName = name;
  }

  @Override
  public void apply(final ChangeListWorker worker) {
    myOldComment = worker.editComment(myName, myNewComment);

    if (myOldComment != null && !Objects.equals(myOldComment, myNewComment)) {
      myListCopy = worker.getChangeListByName(myName);
    }
    else {
      myListCopy = null; // nothing changed, no notify
    }
  }

  @Override
  public void doNotify(final ChangeListListener listener) {
    if (myListCopy != null && myOldComment != null) {
      listener.changeListCommentChanged(myListCopy, myOldComment);
    }
  }

  @Nullable
  public String getOldComment() {
    return myOldComment;
  }
}
