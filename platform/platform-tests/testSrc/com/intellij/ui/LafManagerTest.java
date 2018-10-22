// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.testFramework.PlatformTestCase;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class LafManagerTest extends PlatformTestCase {
  public void testCustomFont() {
    UISettingsState uiSettings = UISettings.getInstance().getState();
    final String fontFace = uiSettings.getFontFace();
    final int fontSize = UISettings.getInstance().getFontSize();
    final LafManagerImpl lafManager = LafManagerImpl.getTestInstance();

    try {
      String newFontName = "Arial";
      int newFontSize = 17;
      uiSettings.setFontFace(newFontName);
      uiSettings.setFontSize(newFontSize);
      uiSettings.setOverrideLafFonts(true);
      lafManager.updateUI();
      Font font = UIManager.getFont("Label.font");
      assertEquals("Font name is not changed", newFontName, font.getName());
      assertEquals("Font size is not changed", newFontSize, font.getSize());
    } finally {
      uiSettings.setOverrideLafFonts(false);
      uiSettings.setFontFace(fontFace);
      uiSettings.setFontSize(fontSize);
      lafManager.updateUI();
    }
  }
}
