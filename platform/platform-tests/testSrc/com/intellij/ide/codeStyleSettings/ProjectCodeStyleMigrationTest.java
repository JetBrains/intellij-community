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

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.ProjectServiceContainerCustomizer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.LegacyCodeStyleSettingsManager;
import com.intellij.psi.codeStyle.ProjectCodeStyleSettingsManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

import static com.intellij.psi.codeStyle.CodeStyleScheme.CODE_STYLE_TAG_NAME;

public class ProjectCodeStyleMigrationTest extends CodeStyleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeStyle.dropTemporarySettings(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CodeStyle.getSettings(getProject()).copyFrom(CodeStyleSettings.getDefaults());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("deprecation")
  public void testMigrateDefault() {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(getProject());
    assertInstanceOf(settingsManager, ProjectCodeStyleSettingsManager.class);
    CodeStyleSettings projectSettings = settingsManager.getCurrentSettings();
    assertTrue(settingsManager.USE_PER_PROJECT_SETTINGS);
    assertEquals(CodeStyleSettings.getDefaults(), projectSettings);
    Element state = settingsManager.getState();
    Element codeStyle = state.getChild(CODE_STYLE_TAG_NAME);
    assertNull(codeStyle);
  }

  @SuppressWarnings("deprecation")
  public void testMigrateChanged() {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(getProject());
    assertInstanceOf(settingsManager, ProjectCodeStyleSettingsManager.class);
    CodeStyleSettings projectSettings = settingsManager.getCurrentSettings();
    assertTrue(settingsManager.USE_PER_PROJECT_SETTINGS);
    assertEquals(77, projectSettings.RIGHT_MARGIN);
    assertEmpty(projectSettings.FIELD_NAME_PREFIX);
    Element state = settingsManager.getState();
    Element codeStyle = state.getChild(CODE_STYLE_TAG_NAME);
    assertNotNull(codeStyle);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    setupLegacyManager();
    return super.getProjectDescriptor();
  }

  private void setupLegacyManager() {
    ProjectServiceContainerCustomizer.getEp().maskAll(Collections.singletonList(project -> {
      try {
        LegacyCodeStyleSettingsManager legacyCodeStyleSettingsManager = new LegacyCodeStyleSettingsManager();
        Document document = JDOMUtil.loadDocument(new File(getTestDataPath() + getTestName(true) + ".xml"));
        legacyCodeStyleSettingsManager.loadState(document.getRootElement());
        ServiceContainerUtil.registerServiceInstance(project, LegacyCodeStyleSettingsManager.class, legacyCodeStyleSettingsManager);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }), getTestRootDisposable(), false);
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "projectSettingsMigration";
  }
}
