// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class NewProjectSettingsTest extends CodeStyleTestCase {
  private final static Map<String,Runnable> ourSetupMap = new HashMap<>();

  static {
    ourSetupMap.put("nonDefaultSettings", () -> {
      CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance();
      manager.USE_PER_PROJECT_SETTINGS = true;
      CodeStyleSettings testSettings = CodeStyle.createTestSettings();
      manager.setMainProjectCodeStyle(testSettings);
      testSettings.setDefaultRightMargin(77);
    });
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      restoreDefaults();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  //@Override
  //protected void setupProject() {
  //  Runnable setupRunnable = ourSetupMap.get(getTestName(true));
  //  if (setupRunnable != null) setupRunnable.run();
  //}

  private void restoreDefaults() {
    restoreDefaults(CodeStyleSettingsManager.getInstance());
    restoreDefaults(CodeStyleSettingsManager.getInstance(getProject()));
  }

  private static void restoreDefaults(@NotNull CodeStyleSettingsManager manager) {
    manager.USE_PER_PROJECT_SETTINGS = false;
    manager.setMainProjectCodeStyle(null);
  }

  public void testNonDefaultSettings() {
    //CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    //CodeStyleSettings appSettings = CodeStyleSettingsManager.getInstance().getMainProjectCodeStyle();
    //assertNotNull(appSettings);
    //assertNotSame(settings, appSettings);
    //assertEquals(settings, appSettings);
  }
}
