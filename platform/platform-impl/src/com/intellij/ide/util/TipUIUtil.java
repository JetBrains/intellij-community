/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

/**
 * @author dsl
 */
public class TipUIUtil {
  @NonNls private static final String SHORTCUT_ENTITY = "&shortcut:";

  private TipUIUtil() {
  }

  public static void openTipInBrowser(String tipPath, JEditorPane browser, Class providerClass) {
    /* TODO: detect that file is not present
    if (!file.exists()) {
      browser.read(new StringReader("Tips for '" + feature.getDisplayName() + "' not found.  Make sure you installed IntelliJ IDEA correctly."), null);
      return;
    }
    */
    try {
      if (tipPath == null) return;
      if (providerClass == null) providerClass = TipUIUtil.class;
      URL url = ResourceUtil.getResource(providerClass, "/tips/", tipPath);

      if (url == null) {
        setCantReadText(browser, tipPath);
        return;
      }

      StringBuffer text = new StringBuffer(ResourceUtil.loadText(url));
      updateShortcuts(text);
      String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
      replaced = replaced.replace("&majorVersion;", ApplicationInfo.getInstance().getMajorVersion());
      replaced = replaced.replace("&minorVersion;", ApplicationInfo.getInstance().getMinorVersion());
      if (UIUtil.isUnderDarcula()) {
        replaced = replaced.replace("css/tips.css", "css/tips_darcula.css");
      }
      browser.read(new StringReader(replaced), url);
    }
    catch (IOException e) {
      setCantReadText(browser, tipPath);
    }
  }

  private static void setCantReadText(JEditorPane browser, String missingFile) {
    try {
      browser.read(new StringReader(
        IdeBundle.message("error.unable.to.read.tip.of.the.day", missingFile, ApplicationNamesInfo.getInstance().getFullProductName())), null);
    }
    catch (IOException ignored) {
    }
  }

  private static void updateShortcuts(StringBuffer text) {
    int lastIndex = 0;
    while(true) {
      lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
      if (lastIndex < 0) return;
      final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
      int actionIdEnd = text.indexOf(";", actionIdStart);
      if (actionIdEnd < 0) {
        return;
      }
      final String actionId = text.substring(actionIdStart, actionIdEnd);
      String shortcutText = getShortcutText(actionId, KeymapManager.getInstance().getActiveKeymap());
      if (shortcutText == null) {
        Keymap defKeymap = KeymapManager.getInstance().getKeymap(DefaultKeymap.getInstance().getDefaultKeymapName());
        if (defKeymap != null) {
          shortcutText = getShortcutText(actionId, defKeymap);
          if (shortcutText != null) {
            shortcutText += " in default keymap";
          }
        }
      }
      if (shortcutText == null) {
        shortcutText = "<no shortcut for action " + actionId + ">";
      }
      text.replace(lastIndex, actionIdEnd + 1, shortcutText);
      lastIndex += shortcutText.length();
    }
  }

  @Nullable
  private static String getShortcutText(String actionId, Keymap keymap) {
    for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
      if (shortcut instanceof KeyboardShortcut) {
        return KeymapUtil.getShortcutText(shortcut);
      }
    }
    return null;
  }
}
