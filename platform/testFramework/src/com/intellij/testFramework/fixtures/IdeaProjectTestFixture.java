// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  default Disposable getTestRootDisposable() {
    return ((ProjectEx)getProject()).getEarlyDisposable();
  }
}
