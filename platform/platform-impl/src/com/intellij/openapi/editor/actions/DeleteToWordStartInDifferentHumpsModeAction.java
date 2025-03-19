// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

public final class DeleteToWordStartInDifferentHumpsModeAction extends TextComponentEditorAction {
  public DeleteToWordStartInDifferentHumpsModeAction() {
    super(new DeleteToWordBoundaryHandler(true, true));
  }
}
