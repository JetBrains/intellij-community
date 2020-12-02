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

import com.android.tools.idea.gradle.dsl.api.dependencies.ExcludedDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExcludedDependencyModelImpl implements ExcludedDependencyModel {
  @NonNls private static final String GROUP = "group";
  @NonNls private static final String MODULE = "module";

  @NotNull private GradleDslExpressionMap myExcludeMap;

  public ExcludedDependencyModelImpl(@NotNull GradleDslExpressionMap excludeMap) {
    myExcludeMap = excludeMap;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel group() {
    return GradlePropertyModelBuilder.create(myExcludeMap, GROUP).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel module() {
    return GradlePropertyModelBuilder.create(myExcludeMap, MODULE).buildResolved();
  }
}
