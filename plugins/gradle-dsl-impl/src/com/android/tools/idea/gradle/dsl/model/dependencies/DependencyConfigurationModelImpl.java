/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ExcludedDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DependencyConfigurationModelImpl implements DependencyConfigurationModel {
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String FORCE = "force";
  @NonNls private static final String TRANSITIVE = "transitive";

  @NotNull GradleDslClosure myConfigurationElement;

  public DependencyConfigurationModelImpl(@NotNull GradleDslClosure configurationElement) {
    myConfigurationElement = configurationElement;
  }

  @Override
  @NotNull
  public List<ExcludedDependencyModel> excludes() {
    List<ExcludedDependencyModel> excludedDependencies = new ArrayList<>();
    for (GradleDslExpressionMap excludeElement : myConfigurationElement.getPropertyElements(EXCLUDE, GradleDslExpressionMap.class)) {
      excludedDependencies.add(new ExcludedDependencyModelImpl(excludeElement));
    }
    return excludedDependencies;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel force() {
    return GradlePropertyModelBuilder.create(myConfigurationElement, FORCE).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel transitive() {
    return GradlePropertyModelBuilder.create(myConfigurationElement, TRANSITIVE).buildResolved();
  }
}
