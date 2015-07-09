/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class GenericKeymapManager extends DefaultKeymap {
  @Override
  public String getDefaultKeymapName() {
    if (SystemInfo.isMac) {
      return KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP;
    }
    else if (SystemInfo.isXWindow) {
      return KeymapManager.X_WINDOW_KEYMAP;
    }
    else {
      return KeymapManager.DEFAULT_IDEA_KEYMAP;
    }
  }

  @Override
  public String getKeymapPresentableName(@NotNull KeymapImpl keymap) {
    final String name = keymap.getName();

    if (getDefaultKeymapName().equals(name)) {
      return "Default";
    }

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name)) {
      return "IntelliJ IDEA Classic" + (SystemInfo.isMac ? " (Windows)" : "");
    }

    if ("Mac OS X".equals(name)) {
      return "IntelliJ IDEA Classic (OS X)";
    }

    return super.getKeymapPresentableName(keymap);
  }
}
