/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.codeStyleSettings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NewProjectSettingsTest extends CodeStyleTestCase {
  private final static Map<String,Runnable> ourSetupMap = ContainerUtilRt.newHashMap();
  static {
    ourSetupMap.put("nonDefaultSettings", () -> {
      CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance();
      manager.USE_PER_PROJECT_SETTINGS = true;
      CodeStyleSettings testSettings = new CodeStyleSettings();
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
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void setupProject() throws Exception {
    Runnable setupRunnable = ourSetupMap.get(getTestName(true));
    if (setupRunnable != null) setupRunnable.run();
  }

  private static void restoreDefaults() {
    restoreDefaults(CodeStyleSettingsManager.getInstance());
    restoreDefaults(CodeStyleSettingsManager.getInstance(getProject()));
  }

  private static void restoreDefaults(@NotNull CodeStyleSettingsManager manager) {
    manager.USE_PER_PROJECT_SETTINGS = false;
    manager.setMainProjectCodeStyle(null);
  }

  public void testNonDefaultSettings() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    CodeStyleSettings appSettings = CodeStyleSettingsManager.getInstance().getMainProjectCodeStyle();
    assertNotNull(appSettings);
    assertNotSame(settings, appSettings);
    assertEquals(settings, appSettings);
  }
}
