package com.intellij.debugger.tree.actions;

import com.intellij.debugger.tree.CommonRenderers;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ShowAllAsHex extends ShowAllAs {

  public ShowAllAsHex() {
    super(CommonRenderers.getInstance().getHexRenderer());
  }

}
