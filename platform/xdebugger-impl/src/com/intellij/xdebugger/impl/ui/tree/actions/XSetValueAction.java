/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XSetValueAction extends XDebuggerTreeActionBase {
  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    Presentation presentation = e.getPresentation();
    if (node instanceof WatchNode) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
    else {
      presentation.setVisible(true);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && node.getValueContainer().getModifier() != null;
  }

  @Override
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    SetValueInplaceEditor.show(node, nodeName);
  }
}
