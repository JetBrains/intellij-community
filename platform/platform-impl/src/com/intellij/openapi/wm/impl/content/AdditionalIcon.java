/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ActiveIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author graann on 02/02/2018
 */
public abstract class AdditionalIcon {

  private final ActiveIcon myIcon;

  public AdditionalIcon(@NotNull Icon icon) {
    myIcon = new ActiveIcon(icon);
  }

  public AdditionalIcon(@Nullable final ActiveIcon activeIcon) {
    myIcon = activeIcon;
  }

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
    return iconRec.y + iconRec.height / 2 - getIconHeight() / 2 + 1;
  }

  public int getIconHeight() {
    return myIcon.getIconHeight();
  }


  public abstract Rectangle getIconRec();

  public abstract boolean isActive();
}
