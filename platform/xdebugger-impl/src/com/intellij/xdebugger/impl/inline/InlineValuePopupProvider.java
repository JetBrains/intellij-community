// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface InlineValuePopupProvider {
  ExtensionPointName<InlineValuePopupProvider> EP_NAME = ExtensionPointName.create("com.intellij.xdebugger.inlineValuePopupProvider");

  boolean accepts(@NotNull XValueNodeImpl xValueNode);

  void showPopup(@NotNull XValueNodeImpl xValueNode,
                 @NotNull XDebugSession session,
                 @NotNull XSourcePosition position,
                 @NotNull XDebuggerTreeCreator treeCreator,
                 @NotNull Editor editor,
                 @NotNull Point point,
                 @Nullable Runnable hideRunnable);
}
