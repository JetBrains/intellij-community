// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;

/**
 * This is to be provided by the test framework and not by plugin authors.
 * <p>
 * A heavy variant can be created using a {@link TestFixtureBuilder} from {@link IdeaTestFixtureFactory#createFixtureBuilder(String)}.
 * <p>
 * A light variant can be created using a {@link TestFixtureBuilder} from {@link IdeaTestFixtureFactory#createLightFixtureBuilder(String)}.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html">Tests and Fixtures (IntelliJ Platform Docs)</a>
 */
public interface IdeaProjectTestFixture extends IdeaTestFixture {
  Project getProject();

  /**
   * If this is a light fixture, returns the only module present in the project.
   * <p>
   * If this is a heavy fixture, returns the first/primary module.
   * To access other modules present in the project, use {@link com.intellij.openapi.module.ModuleManager ModuleManager}.
   */
  Module getModule();

  default @NotNull Disposable getTestRootDisposable() {
    return ((ProjectEx)getProject()).getEarlyDisposable();
  }
}
