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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;

import java.awt.event.MouseEvent;

/**
 * @author max
 * @since Nov 21, 2003
 */
class DefaultKeymapImpl extends KeymapImpl {
  public boolean canModify() {
    return false;
  }

  public String getPresentableName() {
    return DefaultKeymap.getInstance().getKeymapPresentableName(this);
  }

  public void readExternal(Element keymapElement, Keymap[] existingKeymaps) throws InvalidDataException {
    super.readExternal(keymapElement, existingKeymaps);

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(getName()) && !SystemInfo.isXWindow) {
      addShortcut(IdeActions.ACTION_GOTO_DECLARATION, new MouseShortcut(MouseEvent.BUTTON2, 0, 1));
    }
  }
}
