// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class TBItem {
  final @NotNull String myUid;
  final @Nullable ItemListener myListener;
  @NotNull ID myNativePeer = ID.NIL; // java wrapper holds native object
  boolean myIsVisible = true;

  @Nullable String myOptionalContextName;

  TBItem(@NotNull String uid, ItemListener listener) { myUid = uid; myListener = listener; }

  @Override
  public String toString() { return myUid; }

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
  void releaseNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    Foundation.invoke(myNativePeer, "release");
    myNativePeer = ID.NIL;
  }

  protected abstract void _updateNativePeer();  // called from EDT
  protected abstract ID _createNativePeer();    // called from AppKit
}
