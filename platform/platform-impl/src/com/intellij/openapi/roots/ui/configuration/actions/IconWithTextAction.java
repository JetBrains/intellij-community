// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;


public abstract class IconWithTextAction extends AnAction implements CustomComponentAction {

  protected IconWithTextAction() {
    this(null, null, null);
  }

  protected IconWithTextAction(String text) {
    this(text, null, null);
  }

  protected IconWithTextAction(String text, String description, Icon icon) {
    super(text, description, icon);
    if (icon == null) {
      getTemplatePresentation().setIcon(EmptyIcon.ICON_0);
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }
  }

  @Override
  public JComponent createCustomComponent(final Presentation presentation) {
    return createCustomComponentImpl(this, presentation);
  }

  public static JComponent createCustomComponentImpl(final AnAction action, final Presentation presentation) {
    return new ActionButtonWithText(action, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
