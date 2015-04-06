/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public JComponent createCustomComponent(final Presentation presentation) {
    return createCustomComponentImpl(this, presentation);
  }

  public static JComponent createCustomComponentImpl(final AnAction action, final Presentation presentation) {
    return new ActionButtonWithText(action, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
