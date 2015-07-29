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
  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name);

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
