// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

public class TBItemSpacing extends TBItem {
  enum TYPE {
    small,
    large,
    flexible
  }
  private final TYPE myType;

  public TBItemSpacing(TYPE t) { myType = t; }

  @Override
  protected ID _register(ID tbOwner) {
    return TouchBarManager.getNSTLibrary().registerSpacing(tbOwner, myType.name());
  }
}
