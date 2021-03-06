/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.junit4;

import org.junit.experimental.categories.Categories;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.List;

public class IdeaSuite48 extends IdeaSuite {
  public IdeaSuite48(List<Runner> runners, String name, Class<?> category) throws InitializationError {
    super(runners, name);
    filterByCategory(category);
  }
  
  public IdeaSuite48(RunnerBuilder builder, Class<?>[] classes, String name, Class<?> category) throws InitializationError {
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
