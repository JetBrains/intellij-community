// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppComponent;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppProject;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppTestSuite;

/**
 * @author Vladislav.Soroka
 * @deprecated use built-in {@link org.gradle.tooling.model.cpp.CppComponent} available since Gradle 4.10
 */
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public final class CppProjectImpl implements CppProject {
  private @Nullable CppComponent mainComponent;
  private @Nullable CppTestSuite testComponent;

  public CppProjectImpl() {
  }

  public CppProjectImpl(CppProject cppProject) {
    CppComponent mainComponent = cppProject.getMainComponent();
    if (mainComponent != null) {
      this.mainComponent = new CppComponentImpl(mainComponent);
    }
    CppTestSuite testComponent = cppProject.getTestComponent();
    if (testComponent != null) {
      this.testComponent = new CppTestSuiteImpl(testComponent);
    }
  }

  @Override
  public @Nullable CppComponent getMainComponent() {
    return mainComponent;
  }

  public void setMainComponent(@Nullable CppComponent mainComponent) {
    this.mainComponent = mainComponent;
  }

  @Override
  public @Nullable CppTestSuite getTestComponent() {
    return testComponent;
  }

  public void setTestComponent(@Nullable CppTestSuite testComponent) {
    this.testComponent = testComponent;
  }
}
