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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class KeymapConfigurable extends SearchableConfigurable.Parent.Abstract {
  private static final Icon icon = IconLoader.getIcon("/general/keymap.png");

  public String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  public Icon getIcon() {
    return icon;
  }
  
  public String getHelpTopic() {
    return "preferences.keymap";
  }


  protected Configurable[] buildConfigurables() {
    KeymapPanel keymap = new KeymapPanel();
    QuickListsPanel quickLists = new QuickListsPanel(keymap);
    quickLists.reset();
    keymap.setQuickListsPanel(quickLists);
    return new Configurable[]{keymap, quickLists};
  }

  @NotNull
  public String getId() {
    return "preferences.keymap.keymap";
  }

  @Override
  public boolean isVisible() {
    return false;
  }
}