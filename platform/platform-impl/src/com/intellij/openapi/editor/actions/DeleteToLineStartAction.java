// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DeleteToLineStartAction extends TextComponentEditorAction {
  public DeleteToLineStartAction() {
    super(new CutLineActionHandler(true));
  }
}
