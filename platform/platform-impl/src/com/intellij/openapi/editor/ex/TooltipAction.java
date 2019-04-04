// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * High level info for showing the action as a part of the error tooltip
 */
public interface TooltipAction {

  @NotNull
  String getText();

  void execute(@NotNull Editor editor);

  void showAllActions(@NotNull Editor editor);
}