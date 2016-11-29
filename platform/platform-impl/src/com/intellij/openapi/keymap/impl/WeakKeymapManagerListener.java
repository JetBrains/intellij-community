/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

class WeakKeymapManagerListener implements KeymapManagerListener {
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
