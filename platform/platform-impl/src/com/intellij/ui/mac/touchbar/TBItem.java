// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;

abstract class TBItem {
  protected ID myNativePeer;

  void register(ID touchBar) {
    myNativePeer = _register(touchBar);
  }

  // NOTE: creates native peer and returns it's id
  protected abstract ID _register(ID tbOwner);
}
