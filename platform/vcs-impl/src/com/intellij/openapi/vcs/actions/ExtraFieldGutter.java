// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

/**
 * The only reason for this column is IDEA-60573
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
final class ExtraFieldGutter extends AnnotationFieldGutter {
  private final @NotNull AnnotateActionGroup myActionGroup;

  ExtraFieldGutter(@NotNull FileAnnotation fileAnnotation,
                   @NotNull AnnotationPresentation presentation,
                   @NotNull Couple<Map<VcsRevisionNumber, Color>> bgColorMap, @NotNull AnnotateActionGroup actionGroup) {
    super(fileAnnotation, presentation, bgColorMap);
    myActionGroup = actionGroup;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    return isAvailable() ? " " : "";
  }

  @Override
  public boolean isAvailable() {
    for (AnAction action : myActionGroup.getChildren(null)) {
      if (action instanceof ShowHideAspectAction && ((ShowHideAspectAction)action).isSelected()) {
        return false;
      }
    }
    return true;
  }
}
