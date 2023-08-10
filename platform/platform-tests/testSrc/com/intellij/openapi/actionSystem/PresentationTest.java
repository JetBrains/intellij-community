// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.testFramework.LightPlatformTestCase;

import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

public class PresentationTest extends LightPlatformTestCase {
  private final Data[] data = new Data[]{
    new Data("No mnemonic", "No mnemonic", "No mnemonic", "No mnemonic", 0, -1),
    new Data("_First char", "&First char", "First char", "_First char", 'F', 0),
    new Data("S_econd char", "S&econd char", "Second char", "S_econd char", 'E', 1),
    new Data("Pre-last and not unique ch_ar", "Pre-last and not unique ch&ar", "Pre-last and not unique char",
             "Pre-last and not unique ch_ar", 'A', 26),
    new Data("Last cha_r", "Last cha&r", "Last char", "Last cha_r", 'R', 8),
    new Data("Too late_", "Too late&", "Too late", "Too late", 0, -1),
    new Data("Do__uble", "Do&_uble", "Do_uble", "Do__uble", 0, -1),
    new Data("Dou_&ble", "Dou&&ble", "Dou&ble", "Dou&&ble", 0, -1),
    new Data("Complete double__", "Complete double&_", "Complete double_", "Complete double__", 0, -1),
    new Data("Complete double_&", "Complete double&&", "Complete double&", "Complete double&&", 0, -1),
    new Data("Repea_te_d", "Repea&te_d", "Repeate_d", "Repea_te_d", 'T', 5),
    new Data("Re_peate&d", "Re&peate&d", "Repeate&d", "Re_peate&d", 'P', 2),
    new Data("Run 'test__1' with Co_verage", "Run 'test__1' with Co&verage", "Run 'test_1' with Coverage",
             "Run 'test__1' with Co_verage", 'V', 20),
    new Data("R_un 'test_1'", "R&un 'test_1'", "Run 'test_1'", "R_un 'test_1'", 'U', 1),
  };

  private static final class Data {
    public final String inputTextsUnderscore;
    public final String inputTextsAmpersand;
    public final String menuText;
    public final String fullMenuText;
    public final int mnemonic;
    public final int index;

    private Data(String inputTextsUnderscore,
                 String inputTextsAmpersand,
                 String menuText,
                 String fullMenuText,
                 int mnemonic,
                 int index) {
      this.inputTextsUnderscore = inputTextsUnderscore;
      this.inputTextsAmpersand = inputTextsAmpersand;
      this.menuText = menuText;
      this.mnemonic = mnemonic;
      this.index = index;
      this.fullMenuText = fullMenuText;
    }
  }

  public void testSetTextWithUnderscores() {
    for (Data testCase : data) {
      Presentation p = new Presentation();
      p.setText(testCase.inputTextsUnderscore);
      assertEquals(testCase.menuText, p.getText());
      assertEquals(testCase.mnemonic, p.getMnemonic());
      assertEquals(testCase.index, p.getDisplayedMnemonicIndex());
      assertEquals(testCase.fullMenuText, p.getTextWithPossibleMnemonic().get().toString());
    }
  }

  public void testSetTextWithAmpersands() {
    for (Data testCase : data) {
      Presentation p = new Presentation();
      p.setText(testCase.inputTextsAmpersand);
      assertEquals(testCase.menuText, p.getText());
      assertEquals(testCase.mnemonic, p.getMnemonic());
      assertEquals(testCase.index, p.getDisplayedMnemonicIndex());
      assertEquals(testCase.fullMenuText, p.getTextWithPossibleMnemonic().get().toString());

      assertTrue(testCase.menuText.length() > p.getDisplayedMnemonicIndex());
    }
  }

  public void testGetTextWithMnemonic() {
    for (Data testCase : data) {
      Presentation p1 = new Presentation();
      p1.setText(testCase.inputTextsUnderscore);

      Presentation p2 = new Presentation();
      p2.setText(p1.getTextWithMnemonic());

      assertEquals(p1.getText(), p2.getText());
      assertEquals(p1.getMnemonic(), p2.getMnemonic());
      assertEquals(p1.getDisplayedMnemonicIndex(), p2.getDisplayedMnemonicIndex());
    }
  }

  public void testMnemonicCharacters() {
    for (Data testCase : data) {
      Presentation p1 = new Presentation();
      p1.setText(testCase.inputTextsAmpersand);
      Presentation p2 = new Presentation();
      p2.setText(testCase.inputTextsUnderscore);

      assertEquals(p1.getText(), p2.getText());
      assertEquals(p1.getMnemonic(), p2.getMnemonic());
      assertEquals(p1.getDisplayedMnemonicIndex(), p2.getDisplayedMnemonicIndex());
    }
  }

  public void testPresentationCopying() {
    for (Data testCase : data) {
      Presentation p1 = new Presentation();
      p1.setText(testCase.inputTextsUnderscore);

      Presentation p2 = new Presentation();
      p2.copyFrom(p1);

      assertEquals(p1.getText(), p2.getText());
      assertEquals(p1.getMnemonic(), p2.getMnemonic());
      assertEquals(p1.getDisplayedMnemonicIndex(), p2.getDisplayedMnemonicIndex());
    }
  }

  public void testPresentationWithDisabledMnemonics() {
    UISettingsState uiSettings = UISettings.getInstance().getState();
    uiSettings.setDisableMnemonics(true);
    uiSettings.setDisableMnemonicsInControls(true);

    for (Data testCase : data) {
      Presentation p = new Presentation();
      p.setText(testCase.inputTextsUnderscore);
      assertEquals(testCase.menuText, p.getText());
      assertEquals(KeyEvent.VK_UNDEFINED, p.getMnemonic());
      assertEquals(-1, p.getDisplayedMnemonicIndex());
    }
  }

  public void testEvents() {
    Presentation p = new Presentation();
    Map<String, String> actualEvents = new HashMap<>();
    p.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        String oldValue = actualEvents.put(evt.getPropertyName(), evt.getOldValue() + "->" + evt.getNewValue());
        assertNull("Repeating event: " + evt.getPropertyName(), oldValue);
      }
    });
    Map<String, String> expectedEvents =
      Map.of("mnemonicKey", "0->73",
             "mnemonicIndex", "-1->1",
             "text", "null->Git",
             "textWithSuffix", "null->Git");
    p.setTextWithMnemonic(() -> TextWithMnemonic.parse("G&it"));
    assertEquals(expectedEvents, actualEvents);

    actualEvents.clear();
    p.setTextWithMnemonic(() -> TextWithMnemonic.parse("Git(&G)"));
    Map<String, String> expectedEvents2 =
      // "text" event is not sent, as text without suffix is not changed
      Map.of("mnemonicKey", "73->71",
             "mnemonicIndex", "1->4",
             "textWithSuffix", "Git->Git(G)");
    assertEquals(expectedEvents2, actualEvents);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    UISettingsState uiSettings = UISettings.getInstance().getState();
    uiSettings.setDisableMnemonics(false);
    uiSettings.setDisableMnemonicsInControls(false);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      UISettingsState defaults = new UISettingsState();
      UISettingsState uiSettings = UISettings.getInstance().getState();
      uiSettings.setDisableMnemonics(defaults.getDisableMnemonics());
      uiSettings.setDisableMnemonicsInControls(defaults.getDisableMnemonicsInControls());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
