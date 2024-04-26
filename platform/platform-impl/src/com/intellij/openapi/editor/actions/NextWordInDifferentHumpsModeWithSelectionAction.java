// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;

public final class NextWordInDifferentHumpsModeWithSelectionAction extends TextComponentEditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public NextWordInDifferentHumpsModeWithSelectionAction() {
    super(new NextPrevWordHandler(true, true, true));
  }
}
