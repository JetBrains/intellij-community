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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteStatusChangeNodeDecorator implements ChangeNodeDecorator {
  private final RemoteRevisionsCache myRemoteRevisionsCache;
  private final ChangeListRemoteState myListState;
  private final int myIdx;

  public RemoteStatusChangeNodeDecorator(@NotNull RemoteRevisionsCache remoteRevisionsCache) {
    this(remoteRevisionsCache, null, -1);
  }

  public RemoteStatusChangeNodeDecorator(@NotNull RemoteRevisionsCache remoteRevisionsCache,
                                         @Nullable ChangeListRemoteState listRemoteState,
                                         int idx) {
    myRemoteRevisionsCache = remoteRevisionsCache;
    myListState = listRemoteState;
    myIdx = idx;
  }

  public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
    final boolean state = myRemoteRevisionsCache.isUpToDate(change);
    if (myListState != null) myListState.report(myIdx, state);
    if (!state) {
      component.append(" ");
      component.append(VcsBundle.message("change.nodetitle.change.is.outdated"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
  }
}
