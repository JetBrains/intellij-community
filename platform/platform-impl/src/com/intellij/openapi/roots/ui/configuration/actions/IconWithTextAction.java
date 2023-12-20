// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;


public abstract class IconWithTextAction extends AnAction implements CustomComponentAction {

  protected IconWithTextAction() {
    super();
  }

  protected IconWithTextAction(String text) {
    this(text, null, null);
  }

  protected IconWithTextAction(@NotNull Supplier<String> dynamicText) {
    this(dynamicText, Presentation.NULL_STRING, null);
  }

  protected IconWithTextAction(String text, String description, Icon icon) {
    this(() -> text, () -> description, icon);
  }

  protected IconWithTextAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
    if (icon == null) {
      getTemplatePresentation().setIcon(EmptyIcon.ICON_0);
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponentImpl(this, presentation, place);
  }

  public static @NotNull JComponent createCustomComponentImpl(@NotNull AnAction action, @NotNull Presentation presentation, @NotNull String place) {
    return new ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
