// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowContentUiType;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author Vladimir Kondratyev
 */
interface InternalDecoratorListener extends EventListener{
  void hidden(@NotNull InternalDecorator source);

  void hiddenSide(@NotNull InternalDecorator source);

  void resized(@NotNull InternalDecorator source);

  void activated(@NotNull InternalDecorator source);

  void contentUiTypeChanges(@NotNull InternalDecorator sources, @NotNull ToolWindowContentUiType type);

  void visibleStripeButtonChanged(@NotNull InternalDecorator source, boolean visible);
}
