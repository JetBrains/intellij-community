// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

public abstract class TBItemButton extends TBItem {
  protected final TBItemCallback myAction;

  protected TBItemButton(TBItemCallback action) { myAction = action; }
}
