// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

final class WeakKeymapManagerListener implements KeymapManagerListener {
  private final KeymapManagerEx myKeymapManager;
  private final Reference<KeymapManagerListener> myRef;

  WeakKeymapManagerListener(@NotNull KeymapManagerEx keymapManager, @NotNull KeymapManagerListener delegate) {
    myKeymapManager = keymapManager;
    myRef = new WeakReference<>(delegate);
  }

  public boolean isDead() {
    return myRef.get() == null;
  }

  public boolean isWrapped(@NotNull KeymapManagerListener listener) {
    return myRef.get() == listener;
  }

  @Override
  public void activeKeymapChanged(Keymap keymap) {
    KeymapManagerListener delegate = myRef.get();
    if (delegate == null) {
      myKeymapManager.removeKeymapManagerListener(this);
    }
    else {
      delegate.activeKeymapChanged(keymap);
    }
  }
}
