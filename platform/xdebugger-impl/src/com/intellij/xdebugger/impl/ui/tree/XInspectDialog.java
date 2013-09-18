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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.XValueHintTreeComponent;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XInspectDialog extends DialogWrapper {
  private final XValueHintTreeComponent myTreePanel;

  public XInspectDialog(@NotNull Project project,
                        XDebuggerEditorsProvider editorsProvider,
                        XSourcePosition sourcePosition,
                        @NotNull String name,
                        @NotNull XValue value,
                        XValueMarkers<?, ?> markers) {
    super(project, false);

    setTitle(XDebuggerBundle.message("inspect.value.dialog.title", name));
    setModal(false);

    XDebuggerTree tree = new XDebuggerTree(project, editorsProvider, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, markers);
    myTreePanel = new XValueHintTreeComponent(null, tree, Pair.create(value, name));
    init();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myTreePanel.getMainPanel();
  }

  @Override
  @Nullable
  protected JComponent createSouthPanel() {
    return null;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "#xdebugger.XInspectDialog";
  }
}