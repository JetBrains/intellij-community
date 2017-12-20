/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.IdeaTestFixtureFactoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is to be provided by IDEA and not by plugin authors.
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
  public abstract <T extends ModuleFixtureBuilder> void registerFixtureBuilder(@NotNull Class<T> aClass, @NotNull Class<? extends T> implClass);

  public abstract void registerFixtureBuilder(@NotNull Class<? extends ModuleFixtureBuilder> aClass, @NotNull String implClassName);

  @NotNull
  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name) {
    return createFixtureBuilder(name, false);
  }

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, boolean isDirectoryBasedProject);

  @NotNull
  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder();

  @NotNull
  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor);

  @NotNull
  public abstract CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture);

  @NotNull
  public abstract CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirFixture);

  @NotNull
  public abstract TempDirTestFixture createTempDirTestFixture();

  @NotNull
  public abstract BareTestFixture createBareFixture();
}
