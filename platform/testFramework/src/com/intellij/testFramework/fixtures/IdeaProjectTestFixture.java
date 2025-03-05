// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;

/**
 * This is to be provided by the test framework and not by plugin authors.
 *
 * @see IdeaTestFixtureFactory#createFixtureBuilder(String)
 * @see IdeaTestFixtureFactory#createLightFixtureBuilder(String)
 */
public interface IdeaProjectTestFixture extends IdeaTestFixture {
  Project getProject();

  Module getModule();

  default @NotNull Disposable getTestRootDisposable() {
    return ((ProjectEx)getProject()).getEarlyDisposable();
  }
}
