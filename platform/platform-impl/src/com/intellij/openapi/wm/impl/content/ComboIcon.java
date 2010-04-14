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
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.openapi.util.IconLoader;

import java.awt.*;

public abstract class ComboIcon {

  private final ActiveIcon myIcon = new ActiveIcon(IconLoader.getIcon("/general/combo.png"));

  public void paintIcon(final Component c, final Graphics g) {
    myIcon.setActive(isActive());

    final Rectangle moreRect = getIconRec();

    if (moreRect == null) return;

    int iconY = getIconY(moreRect);
    int iconX = getIconX(moreRect);


    myIcon.paintIcon(c, g, iconX, iconY);
  }

  protected int getIconX(final Rectangle iconRec) {
    return iconRec.x + iconRec.width / 2 - getIconWidth() / 2;
  }

  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  protected int getIconY(final Rectangle iconRec) {
    return iconRec.y + iconRec.height / 2 - getIconHeight() / 2;
  }

  public int getIconHeight() {
    return myIcon.getIconHeight();
  }


  public abstract Rectangle getIconRec();

  public abstract boolean isActive();

}
