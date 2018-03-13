// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import org.jetbrains.annotations.NotNull;

abstract class TBItem {
  protected String myItemID; // used as unique item's name inside native code

  String getItemId() { return myItemID; }
  void register(@NotNull String uid) {
    myItemID = uid;
    _register();
  }

  protected abstract void _register();
}
