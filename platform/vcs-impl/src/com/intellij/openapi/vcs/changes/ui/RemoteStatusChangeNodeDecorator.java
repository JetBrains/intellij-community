// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
    final boolean state = myRemoteRevisionsCache.isUpToDate(change);
    if (myListState != null) myListState.report(myIdx, state);
    if (!state) {
      component.append(" ");
      component.append(VcsBundle.message("change.nodetitle.change.is.outdated"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
  }
}
