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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditComment implements ChangeListCommand {
  private final String myName;
  private final String myNewComment;

  private String myOldComment;
  private LocalChangeList myListCopy;

  public EditComment(@NotNull String name, @NotNull String newComment) {
    myNewComment = newComment;
    myName = name;
  }

  public void apply(final ChangeListWorker worker) {
    myOldComment = worker.editComment(myName, myNewComment);

    if (myOldComment != null && !Comparing.equal(myOldComment, myNewComment)) {
      myListCopy = worker.getChangeListByName(myName);
    }
    else {
      myListCopy = null; // nothing changed, no notify
    }
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    if (myListCopy != null) {
      dispatcher.getMulticaster().changeListCommentChanged(myListCopy, myOldComment);
    }
  }

  @Nullable
  public String getOldComment() {
    return myOldComment;
  }
}
