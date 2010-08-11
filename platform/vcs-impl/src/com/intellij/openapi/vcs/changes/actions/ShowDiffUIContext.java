/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.diff.DiffNavigationContext;
import org.jetbrains.annotations.NotNull;

public class ShowDiffUIContext {
  private ShowDiffAction.DiffExtendUIFactory myActionsFactory;
  private boolean myShowFrame;
  private DiffNavigationContext myDiffNavigationContext;

  public ShowDiffUIContext() {
    myActionsFactory = ShowDiffAction.DiffExtendUIFactory.NONE;
  }

  public ShowDiffUIContext(boolean showFrame) {
    myShowFrame = showFrame;
    myActionsFactory = ShowDiffAction.DiffExtendUIFactory.NONE;
  }

  public ShowDiffAction.DiffExtendUIFactory getActionsFactory() {
    return myActionsFactory;
  }

  public void setActionsFactory(@NotNull ShowDiffAction.DiffExtendUIFactory actionsFactory) {
    this.myActionsFactory = actionsFactory;
  }

  public DiffNavigationContext getDiffNavigationContext() {
    return myDiffNavigationContext;
  }

  public void setDiffNavigationContext(DiffNavigationContext diffNavigationContext) {
    myDiffNavigationContext = diffNavigationContext;
  }

  public boolean isShowFrame() {
    return myShowFrame;
  }

  public void setShowFrame(boolean showFrame) {
    this.myShowFrame = showFrame;
  }
}
