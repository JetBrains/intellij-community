/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.VectorDrawablesOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ProductFlavorModel extends FlavorTypeModel {
  @NotNull
  ResolvedPropertyModel applicationId();

  @NotNull
  ResolvedPropertyModel dimension();

  @NotNull
  ExternalNativeBuildOptionsModel externalNativeBuild();

  void removeExternalNativeBuild();

  @NotNull
  ResolvedPropertyModel maxSdkVersion();

  @NotNull
  ResolvedPropertyModel minSdkVersion();

  /**
   * @return resolved property model representing a missing dimension strategy, this will be in the form of a LIST_TYPE with the first item
   * the dimension and the succeeding items the fallbacks. This property should be kept as a LIST_TYPE property. They can be deleted by
   * calling {@link ResolvedPropertyModel#delete()} on the strategy.
   */
  @NotNull
  List<ResolvedPropertyModel> missingDimensionStrategies();

  @NotNull
  ResolvedPropertyModel addMissingDimensionStrategy(@NotNull String dimension, @NotNull Object... fallbacks);

  /**
   * @return whether of new any missingDimensionStrategy has been changed, added or removed.
   */
  boolean areMissingDimensionStrategiesModified();

  @NotNull
  NdkOptionsModel ndk();

  void removeNdk();

  @NotNull
  ResolvedPropertyModel resConfigs();

  @NotNull
  ResolvedPropertyModel renderscriptTargetApi();

  @NotNull
  ResolvedPropertyModel renderscriptSupportModeEnabled();

  @NotNull
  ResolvedPropertyModel renderscriptSupportModelBlasEnabled();

  @NotNull
  ResolvedPropertyModel renderscriptNdkModeEnabled();

  @NotNull
  ResolvedPropertyModel targetSdkVersion();

  @NotNull
  ResolvedPropertyModel testApplicationId();

  @NotNull
  ResolvedPropertyModel testFunctionalTest();

  @NotNull
  ResolvedPropertyModel testHandleProfiling();

  @NotNull
  ResolvedPropertyModel testInstrumentationRunner();

  @NotNull
  ResolvedPropertyModel testInstrumentationRunnerArguments();

  @NotNull
  ResolvedPropertyModel versionCode();

  @NotNull
  ResolvedPropertyModel versionName();

  @NotNull
  VectorDrawablesOptionsModel vectorDrawables();

  @NotNull
  ResolvedPropertyModel wearAppUnbundled();
}
