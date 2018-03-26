// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

public class TBItemButtonText extends TBItemButton {
  private final String myText;

  public TBItemButtonText(String text, TBItemCallback action) {
    super(action);
    myText = text;
  }

  @Override
  public ID _register(ID tbOwner) {
    return TouchBarManager.getNSTLibrary().registerButtonText(tbOwner, myText, myAction);
  }
}
