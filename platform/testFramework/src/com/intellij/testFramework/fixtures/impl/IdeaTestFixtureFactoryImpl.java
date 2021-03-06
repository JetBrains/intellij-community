// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public final class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  private final Map<Class<? extends ModuleFixtureBuilder<?>>, Class<? extends ModuleFixtureBuilder<?>>> myFixtureBuilderProviders = new HashMap<>();

  public IdeaTestFixtureFactoryImpl() {
    registerFixtureBuilder(EmptyModuleFixtureBuilder.class, MyEmptyModuleFixtureBuilderImpl.class);
  }

  @Override
  public final <T extends ModuleFixtureBuilder<?>> void registerFixtureBuilder(@NotNull Class<T> aClass, @NotNull Class<? extends T> implClass) {
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

  @NotNull
  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name, boolean isDirectoryBasedProject) {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl(name, null, isDirectoryBasedProject), myFixtureBuilderProviders);
  }

  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name,
                                                                         @Nullable Path projectPath,
                                                                         boolean isDirectoryBasedProject) {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl(name, projectPath, isDirectoryBasedProject), myFixtureBuilderProviders);
  }

  @NotNull
  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return createLightFixtureBuilder(null);
  }

  @NotNull
  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor) {
    if (projectDescriptor == null) {
      projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
    }
    return new LightTestFixtureBuilderImpl<>(new LightIdeaTestFixtureImpl(projectDescriptor));
  }

  @NotNull
  @Override
  public CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture) {
    return createCodeInsightFixture(projectFixture, new TempDirTestFixtureImpl());
  }

  @NotNull
  @Override
  public CodeInsightTestFixture createCodeInsightFixture(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirFixture) {
    return new CodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
  }

  @NotNull
  @Override
  public TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTestFixtureImpl();
  }

  @NotNull
  @Override
  public BareTestFixture createBareFixture() {
    return new BareTestFixtureImpl();
  }

  public static final class MyEmptyModuleFixtureBuilderImpl extends EmptyModuleFixtureBuilderImpl {
    public MyEmptyModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    @NotNull
    @Override
    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }
}
