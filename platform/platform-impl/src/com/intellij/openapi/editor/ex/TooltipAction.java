// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

/**
 * High level info for showing the action as a part of the error tooltip
 */
public interface TooltipAction {

  @NlsActions.ActionText @NotNull String getText();

  void execute(@NotNull Editor editor, @Nullable InputEvent event);

  void showAllActions(@NotNull Editor editor);
}