// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

abstract class TBItem {
  final String myUid;
  protected ID myNativePeer = ID.NIL; // java wrapper holds native object
  protected boolean myIsVisible = true;

  TBItem(@NotNull String uid) { myUid = uid; }

  void setVisible(boolean visible) { myIsVisible = visible; }
  boolean isVisible() { return myIsVisible; }

  ID getNativePeer() {
    // called from AppKit (when NSTouchBarDelegate create items)
    if (myNativePeer == ID.NIL)
      myNativePeer = _createNativePeer();
    return myNativePeer;
  }
  final void updateNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    _updateNativePeer();
  }
  final void releaseNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    _releaseChildBars();
    Foundation.invoke(myNativePeer, "release");
    myNativePeer = ID.NIL;
  }

  protected abstract void _updateNativePeer();  // called from EDT
  protected abstract ID _createNativePeer();    // called from AppKit

  protected void _releaseChildBars() {}         // called from EDT
}
