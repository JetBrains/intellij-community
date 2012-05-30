/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.ActiveIcon;

import javax.swing.*;
import java.awt.*;

public abstract class MoreIcon {

  private final ActiveIcon myLeft =
    new ActiveIcon(AllIcons.General.ComboArrowLeft, AllIcons.General.ComboArrowLeftPassive);
  private final ActiveIcon myRight =
    new ActiveIcon(AllIcons.General.ComboArrowRight, AllIcons.General.ComboArrowRightPassive);

  protected final int myGap = 2;
  protected boolean myLeftPainted;
  protected boolean myRightPainted;

  public void paintIcon(final Component c, final Graphics g) {
    myLeft.setActive(isActive());
    myRight.setActive(isActive());

    final Rectangle moreRect = getIconRec();

    if (moreRect == null) return;

    int iconY = getIconY(moreRect);
    int iconX = getIconX(moreRect);


    if (myLeftPainted && myRightPainted) {
      myLeft.paintIcon(c, g, iconX, iconY);
      myRight.paintIcon(c, g, iconX + myLeft.getIconWidth() + myGap, iconY);
    }
    else {
      Icon toPaint = myLeftPainted ? myLeft : (myRightPainted ? myRight : null);
      if (toPaint != null) {
        toPaint.paintIcon(c, g, iconX + getIconWidth() / 2 - myGap - 1, iconY);
      }
    }
  }

  protected int getIconX(final Rectangle iconRec) {
    return iconRec.x + iconRec.width / 2 - getIconWidth() / 2;
  }

  protected int getIconY(final Rectangle iconRec) {
    return iconRec.y + iconRec.height / 2 - getIconHeight() / 2;
  }


  protected abstract boolean isActive();

  protected abstract Rectangle getIconRec();

  public void setPaintedIcons(boolean left, boolean right) {
    myLeftPainted = left;
    myRightPainted = right;
  }

  public int getIconWidth() {
    return myLeft.getIconWidth() + myRight.getIconWidth() + myGap;
  }

  public int getIconHeight() {
    return myLeft.getIconHeight();
  }


}
