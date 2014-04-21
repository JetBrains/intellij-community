/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.Presentation;

/**
 * @author Roman.Chernyatchik
 */
public class MenuItemPresentationFactory extends PresentationFactory {
  public static final String HIDE_ICON = "HIDE_ICON";
  private final boolean myForceHide;

  public MenuItemPresentationFactory() {
    this(false);
  }

  public MenuItemPresentationFactory(boolean forceHide) {
    myForceHide = forceHide;
  }

  protected void processPresentation(Presentation presentation) {
    if (!UISettings.getInstance().SHOW_ICONS_IN_MENUS || myForceHide) {
      presentation.setIcon(null);
      presentation.setDisabledIcon(null);
      presentation.setHoveredIcon(null);
      presentation.putClientProperty(HIDE_ICON, Boolean.TRUE);
    }
  }
}
