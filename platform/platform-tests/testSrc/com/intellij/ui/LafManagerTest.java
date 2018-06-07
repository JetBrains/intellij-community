// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.testFramework.PlatformTestCase;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class LafManagerTest extends PlatformTestCase {

  public void testCustomFont() {
    String fontFace = UISettings.getInstance().getFontFace();
    int fontSize = UISettings.getInstance().getFontSize();

    try {
      UISettings instance = UISettings.getInstance();
      String newFontName = "Arial";
      int newFontSize = 17;
      instance.setFontFace(newFontName);
      instance.setFontSize(newFontSize);
      instance.setOverrideLafFonts(true);
      LafManagerImpl lafManager = LafManagerImpl.getTestInstance();
      lafManager.setCurrentLookAndFeel(new DarculaLookAndFeelInfo());
      lafManager.updateUI();
      Font font = UIManager.getFont("Label.font");
      assertEquals("Font name is not changed", newFontName, font.getName());
      assertEquals("Font size is not changed", newFontSize, font.getSize());
    } finally {
      UISettings.getInstance().setFontFace(fontFace);
      UISettings.getInstance().setFontSize(fontSize);
    }
  }
}
