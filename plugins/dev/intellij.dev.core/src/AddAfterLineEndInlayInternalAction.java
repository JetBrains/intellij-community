// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core;

import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class AddAfterLineEndInlayInternalAction extends AbstractAddInlayInternalAction {
  @Override
  protected @Nullable InlayData showDialog(@NotNull Project project, int caretCount) {
    return showTextInlayDialog(DevCoreBundle.message(caretCount > 1
                                                            ? "action.AddAfterLineEndInlayInternalAction.dialog.title.plural"
                                                            : "action.AddAfterLineEndInlayInternalAction.dialog.title"));
  }

  @Override
  protected @Nullable Inlay<?> createInlay(@NotNull InlayModel model,
                                           int offset,
                                           @NotNull InlayData inlayData,
                                           @NotNull PresentationRenderer renderer) {
    return model.addAfterLineEndElement(offset, inlayData.relatesToPrecedingText(), renderer);
  }
}
