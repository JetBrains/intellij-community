// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;

final class HeavyTestFixtureBuilderImpl implements TestFixtureBuilder<IdeaProjectTestFixture> {
  @NotNull
  private final HeavyIdeaTestFixtureImpl myFixture;
  @NotNull
  private final Map<Class<? extends ModuleFixtureBuilder<?>>, Class<? extends ModuleFixtureBuilder<?>>> myProviders;

  HeavyTestFixtureBuilderImpl(@NotNull HeavyIdeaTestFixtureImpl fixture, @NotNull Map<Class<? extends ModuleFixtureBuilder<?>>, Class<? extends ModuleFixtureBuilder<?>>> providers) {
    myFixture = fixture;
    myProviders = providers;
  }

  @NotNull
  @Override
  public HeavyIdeaTestFixture getFixture() {
    return myFixture;
  }

  @Override
  public <M extends ModuleFixtureBuilder<?>> M addModule(@NotNull Class<M> builderClass) {
    loadClassConstants(builderClass);

    Class<? extends ModuleFixtureBuilder<?>> aClass = myProviders.get(builderClass);
    Assert.assertNotNull(builderClass.toString(), aClass);

    Constructor<?>[] constructors = aClass.getDeclaredConstructors();
    if (constructors.length > 1) {
      Arrays.sort(constructors, (c0, c1) -> c1.getParameterCount() - c0.getParameterCount());
    }
    Constructor<?> constructor = constructors[0];
    constructor.setAccessible(true);
    M builder;
    try {
      if (constructor.getParameterCount() == 1) {
        //noinspection unchecked
        builder = (M)constructor.newInstance(this);
      }
      else {
        //noinspection unchecked
        builder = (M)constructor.newInstance();
      }
    }
    catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    myFixture.addModuleFixtureBuilder(builder);
    return builder;
  }

  private static void loadClassConstants(@NotNull Class<?> builderClass) {
    try {
      for (Field field : builderClass.getFields()) {
        field.get(null);
      }
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
