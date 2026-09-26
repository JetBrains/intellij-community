// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core;

import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AddInlayInternalAction extends AbstractAddInlayInternalAction {
  @Override
  protected @Nullable InlayData showDialog(@NotNull Project project, int caretCount) {
    return showTextInlayDialog(DevCoreBundle.message(caretCount > 1
                                                            ? "action.AddInlayInternalAction.dialog.title.plural"
                                                            : "action.AddInlayInternalAction.dialog.title"));
  }

  @Override
  protected @Nullable Inlay<?> createInlay(@NotNull InlayModel model,
                                           int offset,
                                           @NotNull InlayData inlayData,
                                           @NotNull PresentationRenderer renderer) {
    return model.addInlineElement(offset, inlayData.relatesToPrecedingText(), renderer);
  }
}
