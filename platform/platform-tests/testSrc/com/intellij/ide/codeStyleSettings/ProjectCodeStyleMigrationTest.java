// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.LegacyCodeStyleSettingsManager;
import com.intellij.psi.codeStyle.ProjectCodeStyleSettingsManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    setupLegacyManager();
    return super.getProjectDescriptor();
  }

  private void setupLegacyManager() {
    ProjectServiceContainerCustomizer.Companion.getEp().maskAll(Collections.singletonList(project -> {
      try {
        LegacyCodeStyleSettingsManager legacyCodeStyleSettingsManager = new LegacyCodeStyleSettingsManager();
        Element element = JDOMUtil.load(new File(getTestDataPath() + getTestName(true) + ".xml"));
        legacyCodeStyleSettingsManager.loadState(element);
        ServiceContainerUtil.registerServiceInstance(project, LegacyCodeStyleSettingsManager.class, legacyCodeStyleSettingsManager);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }), getTestRootDisposable(), false);
  }

  @Override
  protected @Nullable String getTestDir() {
    return "projectSettingsMigration";
  }
}
