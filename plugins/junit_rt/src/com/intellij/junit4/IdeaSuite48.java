// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit4;

import org.junit.experimental.categories.Categories;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.List;

final class IdeaSuite48 extends IdeaSuite {
  IdeaSuite48(List<Runner> runners, String name, Class<?> category) throws InitializationError {
    super(runners, name);
    filterByCategory(category);
  }
  
  IdeaSuite48(RunnerBuilder builder, Class<?>[] classes, String name, Class<?> category) throws InitializationError {
    super(builder, classes, name);
    filterByCategory(category);
  }

  private void filterByCategory(Class<?> category) {
    if (category != null) {
      try {
        final Categories.CategoryFilter categoryFilter = Categories.CategoryFilter.include(category);
        filter(categoryFilter);
      } catch (NoTestsRemainException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
