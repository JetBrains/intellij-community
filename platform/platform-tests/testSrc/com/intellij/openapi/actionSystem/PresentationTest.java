// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.testFramework.LightPlatformTestCase;

public class PresentationTest extends LightPlatformTestCase {
  private final String[] inputTextsUnderscores = new String[]{"No mnemonic", "_First char",
    "S_econd char", "Pre-last and not unique ch_ar", "Last cha_r", "Too late_", "Do__uble", "Dou_&ble",
    "Complete double__", "Complete double_&", "Repea_te_d", "Re_peate&d", "Run 'test__1' with Co&verage"
    /*,
    "Alphanumeric only _! _@ _# _$ _% _^ _& _* _( _) _- __ _= _+ _| _\\ _/ _0"*/};
  private final String[] inputTextsAmpersands = new String[]{"No mnemonic", "&First char", "S&econd char",
    "Pre-last and not unique ch&ar", "Last cha&r", "Too late&", "Do&_uble", "Dou&&ble", "Complete double&_",
    "Complete double&&", "Repea&te_d", "Re&peate&d"
    /*,
    "Alphanumeric only &! &@ &# &$ &% &^ && &* &( &) &- &_ &= &+ &| &\\ &/ &0"*/};
  private final String[] menuTexts = new String[]{"No mnemonic", "First char", "Second char",
    "Pre-last and not unique char", "Last char", "Too late", "Do_uble", "Dou&ble", "Complete double_",
    "Complete double&", "Repeate_d", "Repeate&d", "Run 'test_1' with Coverage"};
  private final int[] mnemonics = new int[]{0, 'F', 'E', 'A', 'R', 0, 0, 0, 0, 0, 'T', 'P', 'V'};
  private final int[] indeces = new int[]{-1, 0, 1, 26, 8, -1, -1, -1, -1, -1, 5, 2, 20};
  private final String[] fullMenuTexts = new String[]{"No mnemonic", "_First char", "S_econd char",
    "Pre-last and not unique ch_ar", "Last cha_r", "Too late", "Do_uble", "Dou&ble", "Complete double_",
    "Complete double&", "Repea_te_d", "Re_peate&d", "Run 'test_1' with Co_verage"};

  public void testPresentationSetText() {
    for (int i = 0; i < inputTextsUnderscores.length; i++) {
      Presentation p = new Presentation();
      p.setText(inputTextsUnderscores[i]);
      assertEquals(menuTexts[i], p.getText());
      assertEquals(mnemonics[i], p.getMnemonic());
      assertEquals(indeces[i], p.getDisplayedMnemonicIndex());
      assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());
    }
    for (int i = 0; i < inputTextsAmpersands.length; i++) {
      Presentation p = new Presentation();
      p.setText(inputTextsAmpersands[i]);
      assertEquals(menuTexts[i], p.getText());
      assertEquals(mnemonics[i], p.getMnemonic());
      assertEquals(indeces[i], p.getDisplayedMnemonicIndex());
      assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());

      assertTrue(menuTexts[i].length() > p.getDisplayedMnemonicIndex());
    }
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
