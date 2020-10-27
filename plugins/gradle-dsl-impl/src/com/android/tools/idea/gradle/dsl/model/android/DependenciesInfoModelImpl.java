/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.android.DependenciesInfoModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.DependenciesInfoDslElement;
import org.jetbrains.annotations.NotNull;

public class DependenciesInfoModelImpl extends GradleDslBlockModel implements DependenciesInfoModel {
  @NotNull public static final String INCLUDE_IN_APK = "mIncludeInApk";
  @NotNull public static final String INCLUDE_IN_BUNDLE = "mIncludeInBundle";

  public DependenciesInfoModelImpl(@NotNull DependenciesInfoDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel includeInApk() {
    return getModelForProperty(INCLUDE_IN_APK);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel includeInBundle() {
    return getModelForProperty(INCLUDE_IN_BUNDLE);
  }
}
