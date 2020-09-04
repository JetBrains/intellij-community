/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.configurations;

import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConfigurationModelImpl extends GradleDslBlockModel implements ConfigurationModel {
  @NonNls public static final String TRANSITIVE = "mTransitive";
  @NonNls public static final String VISIBLE = "mVisible";

  public ConfigurationModelImpl(@NotNull ConfigurationDslElement element) {
    super(element);
  }


  @NotNull
  @Override
  public String name() {
    return myDslElement.getName();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel transitive() {
    return getModelForProperty(TRANSITIVE);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel visible() {
    return getModelForProperty(VISIBLE);
  }
}
