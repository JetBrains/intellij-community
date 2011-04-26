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
package com.intellij.ui.popup;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

public class PopupIcons {

  public static Icon HAS_NEXT_ICON = IconLoader.getIcon("/icons/ide/nextStep.png");
  public static Icon HAS_NEXT_ICON_GRAYED = IconLoader.getIcon("/icons/ide/nextStepGrayed.png");
  public static Icon HAS_NEXT_ICON_INVERTED = IconLoader.getIcon("/icons/ide/nextStepInverted.png");
  
  public static Icon EMPTY_ICON = new EmptyIcon();

  private static class EmptyIcon implements Icon {
    public int getIconHeight() {
      return HAS_NEXT_ICON.getIconHeight();
    }

    public int getIconWidth() {
      return HAS_NEXT_ICON.getIconWidth();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {

    }
  }
}
