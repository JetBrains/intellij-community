// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Font;
import java.util.Objects;

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
    int fontSize = uiSettings.getFontSize();
    boolean fontEnabled = uiSettings.getOverrideLafFonts();
    LafManagerImpl lafManager = EdtTestUtil.runInEdtAndGet(() -> LafManagerImpl.Companion.getTestInstance());
    try {
      String newFontName = "Arial";
      int newFontSize = 17;
      writeFont(uiSettings, newFontName, newFontSize, true);
      lafManager.updateUI();
      Font font = UIManager.getFont("Label.font");
      assertThat(font.getName()).describedAs("Font name is not changed").isEqualTo(newFontName);
      assertThat(font.getSize()).describedAs("Font size is not changed").isEqualTo(newFontSize);
    }
    finally {
      writeFont(uiSettings, fontFace, fontSize, fontEnabled);
      lafManager.updateUI();
    }
  }

  /**
   * Writes the custom font through the setters of {@link UISettings}, and waits until its getters read it. A setter
   * launches a write of a settings key and returns before the write applies.
   */
  private static void writeFont(UISettings uiSettings, String fontFace, int fontSize, boolean enabled) {
    uiSettings.setFontFace(fontFace);
    uiSettings.setFontSize(fontSize);
    uiSettings.setOverrideLafFonts(enabled);
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.waitWithEventsDispatching(
      "UISettings reads the written font",
      () -> Objects.equals(uiSettings.getFontFace(), fontFace) && uiSettings.getFontSize() == fontSize &&
            uiSettings.getOverrideLafFonts() == enabled,
      10));
  }
}
