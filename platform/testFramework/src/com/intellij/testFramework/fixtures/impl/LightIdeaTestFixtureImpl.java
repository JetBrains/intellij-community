/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.LightIdeaTestFixture;
import com.intellij.util.SmartList;
import com.intellij.util.lang.CompoundRuntimeException;

import java.util.List;

/**
 * @author mike
 */
@SuppressWarnings("TestOnlyProblems")
public class LightIdeaTestFixtureImpl extends BaseFixture implements LightIdeaTestFixture {
  private final LightProjectDescriptor myProjectDescriptor;
  private CodeStyleSettings myOldCodeStyleSettings;

  public LightIdeaTestFixtureImpl(LightProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    IdeaTestApplication application = LightPlatformTestCase.initApplication();
    LightPlatformTestCase.doSetup(myProjectDescriptor, LocalInspectionTool.EMPTY_ARRAY, getTestRootDisposable());
    InjectedLanguageManagerImpl.pushInjectors(getProject());

    myOldCodeStyleSettings = getCurrentCodeStyleSettings().clone();
    myOldCodeStyleSettings.getIndentOptions(StdFileTypes.JAVA);

    application.setDataProvider(new TestDataProvider(getProject()));
  }

  @Override
  public void tearDown() throws Exception {
    Project project = getProject();
    CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
    CodeStyleSettings oldCodeStyleSettings = myOldCodeStyleSettings;
    myOldCodeStyleSettings = null;
    List<Throwable> exceptions = new SmartList<>();
    try {
      UsefulTestCase.doCheckForSettingsDamage(oldCodeStyleSettings, getCurrentCodeStyleSettings(), exceptions);

      LightPlatformTestCase.doTearDown(project, LightPlatformTestCase.getApplication(), true, exceptions);
      super.tearDown();
      InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
      PersistentFS.getInstance().clearIdCache();
      PlatformTestCase.cleanupApplicationCaches(project);
    }
    finally {
      CompoundRuntimeException.throwIfNotEmpty(exceptions);
    }
  }

  @Override
  public Project getProject() {
    return LightPlatformTestCase.getProject();
  }

  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  @Override
  public Module getModule() {
    return LightPlatformTestCase.getModule();
  }
}
