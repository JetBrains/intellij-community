// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

import javax.swing.*;

public class TBItemButtonImg extends TBItemButton {
  private final Icon myIcon;

  public TBItemButtonImg(Icon icon, TBItemCallback action) {
    super(action);
    myIcon = icon;
  }

  @Override
  protected ID _register(ID tbOwner) {
    return TouchBarManager.getNSTLibrary().registerButtonImg(
      tbOwner, NSTLibrary.getRasterFromIcon(myIcon),
      myIcon.getIconWidth(), myIcon.getIconHeight(), myAction
    );
  }
}
