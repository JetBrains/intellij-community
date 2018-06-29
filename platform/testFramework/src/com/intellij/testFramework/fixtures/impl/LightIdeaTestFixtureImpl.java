// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testFramework.fixtures.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.LightIdeaTestFixture;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
@SuppressWarnings("TestOnlyProblems")
public class LightIdeaTestFixtureImpl extends BaseFixture implements LightIdeaTestFixture {
  private final LightProjectDescriptor myProjectDescriptor;
  private SdkLeakTracker myOldSdks;
  private CodeStyleSettingsTracker myCodeStyleSettingsTracker;

  public LightIdeaTestFixtureImpl(@NotNull LightProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    IdeaTestApplication application = LightPlatformTestCase.initApplication();
    LightPlatformTestCase.doSetup(myProjectDescriptor, LocalInspectionTool.EMPTY_ARRAY, getTestRootDisposable());
    InjectedLanguageManagerImpl.pushInjectors(getProject());

    myCodeStyleSettingsTracker = new CodeStyleSettingsTracker(this::getCurrentCodeStyleSettings);

    application.setDataProvider(new TestDataProvider(getProject()));
    myOldSdks = new SdkLeakTracker();
  }

  @Override
  public void tearDown() {
    Project project = getProject();
    CodeStyle.dropTemporarySettings(project);

    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    new RunAll()
      .append(() -> myCodeStyleSettingsTracker.checkForSettingsDamage())
      .append(() -> super.tearDown()) // call all disposables' dispose() while the project is still open
      .append(() -> LightPlatformTestCase.doTearDown(project, LightPlatformTestCase.getApplication()))
      .append(() -> LightPlatformTestCase.checkEditorsReleased())
      .append(() -> {
        SdkLeakTracker oldSdks = myOldSdks;
        if (oldSdks != null) {
          oldSdks.checkForJdkTableLeaks();
        }
      })
      .append(() -> InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project))
      .append(() -> PersistentFS.getInstance().clearIdCache())
      .append(() -> PlatformTestCase.cleanupApplicationCaches(project))
      .run();
  }

  @Override
  public Project getProject() {
    return LightPlatformTestCase.getProject();
  }

  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyle.getSettings(getProject());
  }

  @Override
  public Module getModule() {
    return LightPlatformTestCase.getModule();
  }
}
