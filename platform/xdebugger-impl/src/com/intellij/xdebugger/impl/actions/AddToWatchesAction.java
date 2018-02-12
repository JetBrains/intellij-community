/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.ui.tree.actions.XAddToWatchesTreeAction;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;

final class AddToWatchesAction extends XDebuggerActionBase {
  private static final XAddToWatchesTreeAction TREE_ACTION = new XAddToWatchesTreeAction();

  public AddToWatchesAction() {
    super(true);
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getAddToWatchesActionHandler();
  }

  @Override
  public void update(AnActionEvent event) {
    if (XDebuggerTreeActionBase.getSelectedNode(event.getDataContext()) != null) {
      TREE_ACTION.update(event);
    }
    else {
      super.update(event);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    if (XDebuggerTreeActionBase.getSelectedNode(event.getDataContext()) != null) {
      TREE_ACTION.actionPerformed(event);
    }
    else {
      super.actionPerformed(event);
    }
  }
}
