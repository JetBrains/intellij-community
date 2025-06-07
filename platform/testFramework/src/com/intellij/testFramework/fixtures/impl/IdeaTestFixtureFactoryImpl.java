// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public final class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  private final Map<Class<? extends ModuleFixtureBuilder<?>>, Class<? extends ModuleFixtureBuilder<?>>> myFixtureBuilderProviders = new HashMap<>();

  public IdeaTestFixtureFactoryImpl() {
    registerFixtureBuilder(EmptyModuleFixtureBuilder.class, MyEmptyModuleFixtureBuilderImpl.class);
  }

  @Override
  public <T extends ModuleFixtureBuilder<?>> void registerFixtureBuilder(@NotNull Class<T> aClass, @NotNull Class<? extends T> implClass) {
    myFixtureBuilderProviders.put(aClass, implClass);
  }

  @Override
  public void registerFixtureBuilder(@NotNull Class<? extends ModuleFixtureBuilder<?>> aClass, @NotNull String implClassName) {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends ModuleFixtureBuilder<?>> implClass = (Class<? extends ModuleFixtureBuilder<?>>)Class.forName(implClassName);
      assertTrue(aClass.isAssignableFrom(implClass));
      myFixtureBuilderProviders.put(aClass, implClass);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("Cannot instantiate fixture builder implementation", e);
    }
  }

  @Override
  public @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, boolean isDirectoryBasedProject) {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl(name, null, isDirectoryBasedProject), myFixtureBuilderProviders);
  }

  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name,
                                                                         @NotNull HeavyIdeaTestFixturePathProvider projectPathProvider,
                                                                         boolean isDirectoryBasedProject) {
    return new HeavyTestFixtureBuilderImpl(
      new HeavyIdeaTestFixtureImpl(name, projectPathProvider, isDirectoryBasedProject),
      myFixtureBuilderProviders
    );
  }

  @Override
  public @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String projectName) {
    return createLightFixtureBuilder(null, projectName);
  }

  @Override
  public @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor,
                                                                                       @NotNull String projectName) {
    if (projectDescriptor == null) {
      projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
    }
    return new LightTestFixtureBuilderImpl<>(new LightIdeaTestFixtureImpl(projectDescriptor, projectName));
  }

  @Override
  public @NotNull CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture) {
    return createCodeInsightFixture(projectFixture, new TempDirTestFixtureImpl());
  }

  @Override
  public @NotNull CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirFixture) {
    return new CodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
  }

  @Override
  public @NotNull TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTestFixtureImpl();
  }

  @Override
  public @NotNull BareTestFixture createBareFixture() {
    return new BareTestFixtureImpl();
  }

  public static final class MyEmptyModuleFixtureBuilderImpl extends EmptyModuleFixtureBuilderImpl {
    public MyEmptyModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    @Override
    protected @NotNull ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }
}
