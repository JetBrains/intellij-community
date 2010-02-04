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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoteStatusChangeNodeDecorator implements ChangeNodeDecorator {
  protected final RemoteRevisionsCache myRemoteRevisionsCache;

  public RemoteStatusChangeNodeDecorator(final RemoteRevisionsCache remoteRevisionsCache) {
    myRemoteRevisionsCache = remoteRevisionsCache;
  }

  protected void reportState(final boolean state) {
  }

  public void decorate(final Change change, final SimpleColoredComponent component, boolean isShowFlatten) {
    final boolean state = myRemoteRevisionsCache.isUpToDate(change);
    reportState(state);
    if (! state) {
      component.append(" ");
      component.append(VcsBundle.message("change.nodetitle.change.is.outdated"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Nullable
  public List<Pair<String, Stress>> stressPartsOfFileName(Change change, String parentPath) {
    return null;
  }

  public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
  }
}
