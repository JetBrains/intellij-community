// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class InactiveEditorAction extends EditorAction {
  protected InactiveEditorAction(EditorActionHandler defaultHandler) {
    super(defaultHandler);
  }

  @Override
  protected @Nullable Editor getEditor(final @NotNull DataContext dataContext) {
    return CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
  }
}