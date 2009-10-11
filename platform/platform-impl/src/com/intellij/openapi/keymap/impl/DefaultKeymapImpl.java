/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 21, 2003
 * Time: 9:00:35 PM
 * To change this template use Options | File Templates.
 */
class DefaultKeymapImpl extends KeymapImpl {
  @NonNls
  private static final String DEFAULT = "Default";

  public boolean canModify() {
    return false;
  }

  public String getPresentableName() {
    String name = getName();
    return KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name) ? DEFAULT : name;
  }

  public void readExternal(Element keymapElement, Keymap[] existingKeymaps) throws InvalidDataException {
    super.readExternal(keymapElement, existingKeymaps);

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(getName()) && !SystemInfo.X11PasteEnabledSystem) {
      addShortcut(IdeActions.ACTION_GOTO_DECLARATION, new MouseShortcut(MouseEvent.BUTTON2, 0, 1));
    }
  }
}
