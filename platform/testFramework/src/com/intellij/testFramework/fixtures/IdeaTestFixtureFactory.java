// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

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

  public static @NotNull IdeaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  /**
   * @param aClass test fixture builder interface class
   * @param implClass implementation class, should have a constructor which takes {@link TestFixtureBuilder} as an argument.
   */
  public abstract <T extends ModuleFixtureBuilder<?>> void registerFixtureBuilder(@NotNull Class<T> aClass, @NotNull Class<? extends T> implClass);

  public abstract void registerFixtureBuilder(@NotNull Class<? extends ModuleFixtureBuilder<?>> aClass, @NotNull String implClassName);

  public @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name) {
    return createFixtureBuilder(name, false);
  }

  public abstract @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, boolean isDirectoryBasedProject);

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, @Nullable Path projectPath, boolean isDirectoryBasedProject);

  public abstract @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String projectName);

  public abstract @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor,
                                                                                                @NotNull String projectName);

  public abstract @NotNull CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture);

  public abstract @NotNull CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirFixture);

  public abstract @NotNull TempDirTestFixture createTempDirTestFixture();

  public abstract @NotNull BareTestFixture createBareFixture();
}
