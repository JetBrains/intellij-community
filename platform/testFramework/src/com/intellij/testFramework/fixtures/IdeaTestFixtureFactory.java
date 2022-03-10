// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.IdeaTestFixtureFactoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * This is to be provided by the test framework and not by plugin authors.
 */
public abstract class IdeaTestFixtureFactory {
  private static final IdeaTestFixtureFactory ourInstance = new IdeaTestFixtureFactoryImpl();

  @NotNull
  public static IdeaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  /**
   * @param aClass test fixture builder interface class
   * @param implClass implementation class, should have a constructor which takes {@link TestFixtureBuilder} as an argument.
   */
  public abstract <T extends ModuleFixtureBuilder<?>> void registerFixtureBuilder(@NotNull Class<T> aClass, @NotNull Class<? extends T> implClass);

  public abstract void registerFixtureBuilder(@NotNull Class<? extends ModuleFixtureBuilder<?>> aClass, @NotNull String implClassName);

  @NotNull
  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name) {
    return createFixtureBuilder(name, false);
  }

  public abstract @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, boolean isDirectoryBasedProject);

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, @Nullable Path projectPath, boolean isDirectoryBasedProject);

  @NotNull
  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String projectName);

  @NotNull
  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor,
                                                                                       @NotNull String projectName);
  /**
   * @deprecated Use {@link #createLightFixtureBuilder(LightProjectDescriptor, String)} instead
   */
  @Deprecated
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor) {
    String message = "Use createLightFixtureBuilder(LightProjectDescriptor, String) instead";
    Logger.getInstance(IdeaTestFixtureFactory.class).warn(new RuntimeException(message));
    return createLightFixtureBuilder(projectDescriptor, message);
  }

  @NotNull
  public abstract CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture);

  @NotNull
  public abstract CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirFixture);

  @NotNull
  public abstract TempDirTestFixture createTempDirTestFixture();

  @NotNull
  public abstract BareTestFixture createBareFixture();

  @NotNull
  public abstract SdkTestFixture createSdkFixture();
}
