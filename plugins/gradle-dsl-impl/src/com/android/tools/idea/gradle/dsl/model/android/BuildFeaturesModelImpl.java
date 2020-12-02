/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.BuildFeaturesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.BuildFeaturesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class BuildFeaturesModelImpl extends GradleDslBlockModel implements BuildFeaturesModel {
  @NonNls public static final String COMPOSE = "mCompose";
  @NonNls public static final String ML_MODEL_BINDING = "mMlModelBinding";

  public BuildFeaturesModelImpl(@NotNull BuildFeaturesDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel compose() {
    return getModelForProperty(COMPOSE);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel mlModelBinding() {
    return getModelForProperty(ML_MODEL_BINDING);
  }
}
