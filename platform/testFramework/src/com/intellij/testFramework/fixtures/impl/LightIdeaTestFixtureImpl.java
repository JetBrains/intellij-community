// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.LightIdeaTestFixture;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("TestOnlyProblems")
public final class LightIdeaTestFixtureImpl extends BaseFixture implements LightIdeaTestFixture {
  private final LightProjectDescriptor myProjectDescriptor;
  private final String myName;
  private SdkLeakTracker myOldSdks;
  private CodeStyleSettingsTracker myCodeStyleSettingsTracker;
  private Project myProject;
  private Module myModule;
  private final Disposable mySdkParentDisposable = Disposer.newDisposable("sdk for project in light test fixture");

  public LightIdeaTestFixtureImpl(@NotNull LightProjectDescriptor projectDescriptor, @NotNull String name) {
    myProjectDescriptor = projectDescriptor;
    myName = name;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    TestApplicationManager application = TestApplicationManager.getInstance();
    Pair<Project, Module> setup = LightPlatformTestCase.doSetup(myProjectDescriptor, LocalInspectionTool.EMPTY_ARRAY, getTestRootDisposable(),
                                                                mySdkParentDisposable, myName);
    myProject = setup.getFirst();
    myModule = setup.getSecond();
    InjectedLanguageManagerImpl.pushInjectors(getProject());

    myCodeStyleSettingsTracker = new CodeStyleSettingsTracker(this::getCurrentCodeStyleSettings);

    application.setDataProvider(new TestDataProvider(getProject()));
    myOldSdks = new SdkLeakTracker();
  }

  @Override
  public void tearDown() {
    Project project = getProject();
    if (project != null) {
      CodeStyle.dropTemporarySettings(project);
    }

    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    new RunAll(
      () -> {
        if (myCodeStyleSettingsTracker != null) {
          myCodeStyleSettingsTracker.checkForSettingsDamage();
        }
      },
      () -> {
        if (project != null) {
          TestApplicationManagerKt.waitForProjectLeakingThreads(project);
        }
      },
      () -> super.tearDown(), // call all disposables' dispose() while the project is still open
      () -> {
        myProject = null;
        myModule = null;
        if (project != null) {
          TestApplicationManagerKt.tearDownProjectAndApp(project);
        }
      },
      () -> LightPlatformTestCase.checkEditorsReleased(),
      () -> Disposer.dispose(mySdkParentDisposable),
      () -> {
        SdkLeakTracker oldSdks = myOldSdks;
        if (oldSdks != null) {
          oldSdks.checkForJdkTableLeaks();
        }
      },
      () -> {
        if (project != null) {
          InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
        }
      },
      () -> {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
          ManagingFS managingFS = app.getServiceIfCreated(ManagingFS.class);
          if (managingFS != null) {
            ((PersistentFS)managingFS).clearIdCache();
          }
        }
      },
      () -> HeavyPlatformTestCase.cleanupApplicationCaches(project)
    ).run();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  private CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return CodeStyle.createTestSettings();
    return CodeStyle.getSettings(getProject());
  }

  @Override
  public Module getModule() {
    return myModule;
  }
}
