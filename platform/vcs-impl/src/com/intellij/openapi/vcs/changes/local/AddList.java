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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddList implements ChangeListCommand {
  @NotNull private final String myName;
  @Nullable private final String myComment;
  @Nullable private final Object myData;

  private boolean myWasListCreated;
  private LocalChangeList myNewListCopy;
  private String myOldComment;

  public AddList(@NotNull String name, @Nullable String comment, @Nullable Object data) {
    myName = name;
    myComment = comment;
    myData = data;
  }

  public void apply(final ChangeListWorker worker) {
    LocalChangeList list = worker.getChangeListByName(myName);
    if (list == null) {
      myWasListCreated = true;
      myOldComment = null;
      myNewListCopy = worker.addChangeList(myName, myComment, myData).copy();
    }
    else if (StringUtil.isNotEmpty(myComment)) {
      myWasListCreated = false;
      myOldComment = worker.editComment(myName, myComment);
      myNewListCopy = list.copy();
    }
    else {
      myWasListCreated = false;
      myOldComment = null;
      myNewListCopy = list.copy();
    }
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
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
