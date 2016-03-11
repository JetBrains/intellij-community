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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class WatchNodeImpl extends XValueNodeImpl implements WatchNode {
  private final XExpression myExpression;

  public WatchNodeImpl(@NotNull XDebuggerTree tree, @NotNull WatchesRootNode parent,
                       @NotNull XValue result, @NotNull XExpression expression) {
    super(tree, parent, expression.getExpression(), result);
    myExpression = expression;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return getValuePresentation() instanceof XErrorValuePresentation?
           XDebuggerUIConstants.ERROR_MESSAGE_ICON : AllIcons.Debugger.Watch;
  }

  @Override
  @NotNull
  public XExpression getExpression() {
    return myExpression;
  }
}