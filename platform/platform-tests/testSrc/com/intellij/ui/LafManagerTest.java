// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.NotRoamableUiSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class LafManagerTest {
  @Test
  public void customFont() {
    UISettings uiSettings = UISettings.getInstance();
    String fontFace = uiSettings.getFontFace();
    int fontSize = UISettings.getInstance().getFontSize();
    LafManagerImpl lafManager = EdtTestUtil.runInEdtAndGet(() -> LafManagerImpl.Companion.getTestInstance());
    try {
      String newFontName = "Arial";
      int newFontSize = 17;
      uiSettings.setFontFace(newFontName);
      uiSettings.setFontSize(newFontSize);
      NotRoamableUiSettings.Companion.getInstance().setOverrideLafFonts(true);
      lafManager.updateUI();
      Font font = UIManager.getFont("Label.font");
      assertThat(font.getName()).describedAs("Font name is not changed").isEqualTo(newFontName);
      assertThat(font.getSize()).describedAs("Font size is not changed").isEqualTo(newFontSize);
    }
    finally {
      NotRoamableUiSettings.Companion.getInstance().setOverrideLafFonts(false);
      uiSettings.setFontFace(fontFace);
      uiSettings.setFontSize(fontSize);
      lafManager.updateUI();
    }
  }
}
