// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowContentUiType;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author Vladimir Kondratyev
 */
interface InternalDecoratorListener extends EventListener{
  void hidden(@NotNull InternalDecoratorImpl source);

  void hiddenSide(@NotNull InternalDecoratorImpl source);

  void resized(@NotNull InternalDecoratorImpl source);

  void activated(@NotNull InternalDecoratorImpl source);

  void contentUiTypeChanges(@NotNull InternalDecoratorImpl sources, @NotNull ToolWindowContentUiType type);

  void visibleStripeButtonChanged(@NotNull InternalDecoratorImpl source, boolean visible);
}
