// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.*;

@ApiStatus.Internal
public abstract class TBItem {
  private final @NotNull String myName;
  private @Nullable String myUid;

  protected @NotNull ID myNativePeer = ID.NIL; // java wrapper holds native object

  final @Nullable ItemListener myListener;
  boolean myIsVisible = true;

  TBItem(@NotNull @NonNls String name, @Nullable ItemListener listener) { myName = name; myListener = listener; }

  @Override
  public String toString() { return myUid == null ? String.format("%s [null-uid]", myName) : myUid; }

  @NotNull
  String getName() { return myName; }

  @TestOnly
  public @NotNull ID getNativePeer() {
    return myNativePeer;
  }

  @Nullable
  String getUid() { return myUid; }

  void setUid(@Nullable String uid) { myUid = uid; }

  synchronized
  @NotNull ID createNativePeer() {
    // called from AppKit (when NSTouchBarDelegate create items)
    if (myNativePeer == ID.NIL) {
      myNativePeer = _createNativePeer();
    }
    return myNativePeer;
  }

  synchronized
  void releaseNativePeer() {
    if (myNativePeer == ID.NIL)
      return;
    NST.releaseNativePeer(myNativePeer);
    myNativePeer = ID.NIL;
  }

  protected abstract ID _createNativePeer();    // called from AppKit
}
