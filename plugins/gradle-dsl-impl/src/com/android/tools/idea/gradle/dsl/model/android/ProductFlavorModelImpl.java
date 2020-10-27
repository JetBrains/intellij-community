/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.VectorDrawablesOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.VectorDrawablesOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.android.AbstractProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement.EXTERNAL_NATIVE_BUILD_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;

public final class ProductFlavorModelImpl extends FlavorTypeModelImpl implements ProductFlavorModel {
  /**
   * These are used here and in the construction of Dsl by {@link ProductFlavorDslElement}.
   */
  @NonNls public static final String APPLICATION_ID = "mApplicationId";
  @NonNls public static final String DIMENSION = "mDimension";
  @NonNls public static final String MAX_SDK_VERSION = "mMaxSdkVersion";
  @NonNls public static final String MIN_SDK_VERSION = "mMinSdkVersion";
  @NonNls public static final String MISSING_DIMENSION_STRATEGY = "mMissingDimensionStrategy";
  @NonNls public static final String RENDER_SCRIPT_TARGET_API = "mRenderscriptTargetApi";
  @NonNls public static final String RENDER_SCRIPT_SUPPORT_MODE_ENABLED = "mRenderscriptSupportModeEnabled";
  @NonNls public static final String RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED = "mRenderscriptSupportModeBlasEnabled";
  @NonNls public static final String RENDER_SCRIPT_NDK_MODE_ENABLED = "mRenderscriptNdkModeEnabled";
  @NonNls public static final String RES_CONFIGS = "mResConfigs";
  @NonNls public static final String TARGET_SDK_VERSION = "mTargetSdkVersion";
  @NonNls public static final String TEST_APPLICATION_ID = "mTestApplicationId";
  @NonNls public static final String TEST_FUNCTIONAL_TEST = "mTestFunctionalTest";
  @NonNls public static final String TEST_HANDLE_PROFILING = "mTestHandleProfiling";
  @NonNls public static final String TEST_INSTRUMENTATION_RUNNER = "mTestInstrumentationRunner";
  @NonNls public static final String TEST_INSTRUMENTATION_RUNNER_ARGUMENTS = "mTestInstrumentationRunnerArguments";
  @NonNls public static final String VERSION_CODE = "mVersionCode";
  @NonNls public static final String VERSION_NAME = "mVersionName";
  @NonNls public static final String WEAR_APP_UNBUNDLED = "mWearAppUnbundled";


  public ProductFlavorModelImpl(@NotNull AbstractProductFlavorDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel applicationId() {
    return getModelForProperty(APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel dimension() {
    return getModelForProperty(DIMENSION);
  }

  @Override
  @NotNull
  public ExternalNativeBuildOptionsModel externalNativeBuild() {
    ExternalNativeBuildOptionsDslElement externalNativeBuildOptionsDslElement =
      myDslElement.ensurePropertyElement(EXTERNAL_NATIVE_BUILD_OPTIONS);
    return new ExternalNativeBuildOptionsModelImpl(externalNativeBuildOptionsDslElement);
  }

  @Override
  public void removeExternalNativeBuild() {
    myDslElement.removeProperty(EXTERNAL_NATIVE_BUILD_OPTIONS.name);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel maxSdkVersion() {
    return getModelForProperty(MAX_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel minSdkVersion() {
    return getModelForProperty(MIN_SDK_VERSION);
  }

  @NotNull
  @Override
  public List<ResolvedPropertyModel> missingDimensionStrategies() {
    List<ResolvedPropertyModel> models = new ArrayList<>();
    for (GradleDslExpressionList list : myDslElement.getPropertyElements(MISSING_DIMENSION_STRATEGY, GradleDslExpressionList.class)) {
      if (list.getExpressions().size() > 1) {
        models.add(GradlePropertyModelBuilder.create(list).buildResolved());
      }
    }
    return models;
  }

  @NotNull
  @Override
  public ResolvedPropertyModel addMissingDimensionStrategy(@NotNull String dimension, @NotNull Object... fallbacks) {
    GradleDslExpressionList list = new GradleDslExpressionList(myDslElement, GradleNameElement.create("missingDimensionStrategy"), false);
    myDslElement.setNewElement(list);
    list.setElementType(REGULAR);
    list.setModelEffect(new ModelEffectDescription(new ModelPropertyDescription(MISSING_DIMENSION_STRATEGY), OTHER));
    ResolvedPropertyModel model = GradlePropertyModelBuilder.create(list).buildResolved();
    model.addListValue().setValue(dimension);
    for (Object fallback : fallbacks) {
      model.addListValue().setValue(fallback);
    }
    return model;
  }

  @Override
  public boolean areMissingDimensionStrategiesModified() {
    List<GradleDslElement> originalElements =
      myDslElement.getOriginalElements().stream().filter(e -> e.getName().equals(MISSING_DIMENSION_STRATEGY)).collect(Collectors.toList());
    List<GradleDslElement> currentElements = myDslElement.getPropertyElementsByName(MISSING_DIMENSION_STRATEGY);
    if (originalElements.size() != currentElements.size()) {
      return true;
    }
    for (GradleDslElement oldElement : originalElements) {
      boolean modified = true;
      for (GradleDslElement newElement : currentElements) {
        modified &= PropertyUtil.isElementModified(oldElement, newElement);
      }
      if (modified) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public NdkOptionsModel ndk() {
    NdkOptionsDslElement ndkOptionsDslElement = myDslElement.ensurePropertyElement(NDK_OPTIONS);
    return new NdkOptionsModelImpl(ndkOptionsDslElement);
  }

  @Override
  public void removeNdk() {
    myDslElement.removeProperty(NDK_OPTIONS.name);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resConfigs() {
    return getModelForProperty(RES_CONFIGS);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptTargetApi() {
    return getModelForProperty(RENDER_SCRIPT_TARGET_API);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptSupportModeEnabled() {
    return getModelForProperty(RENDER_SCRIPT_SUPPORT_MODE_ENABLED);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptSupportModelBlasEnabled() {
    return getModelForProperty(RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel renderscriptNdkModeEnabled() {
    return getModelForProperty(RENDER_SCRIPT_NDK_MODE_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel targetSdkVersion() {
    return getModelForProperty(TARGET_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testApplicationId() {
    return getModelForProperty(TEST_APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testFunctionalTest() {
    return getModelForProperty(TEST_FUNCTIONAL_TEST);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testHandleProfiling() {
    return getModelForProperty(TEST_HANDLE_PROFILING);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testInstrumentationRunner() {
    return getModelForProperty(TEST_INSTRUMENTATION_RUNNER);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testInstrumentationRunnerArguments() {
    GradleDslExpressionMap testInstrumentationRunnerArguments = myDslElement.getPropertyElement(GradleDslExpressionMap.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
    if (testInstrumentationRunnerArguments == null) {
      myDslElement.addDefaultProperty(new GradleDslExpressionMap(myDslElement, GradleNameElement.fake(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS)));
    }
    return getModelForProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionCode() {
    return getModelForProperty(VERSION_CODE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionName() {
    return getModelForProperty(VERSION_NAME);
  }

  @NotNull
  @Override
  public VectorDrawablesOptionsModel vectorDrawables() {
    VectorDrawablesOptionsDslElement vectorDrawableElement = myDslElement.ensurePropertyElement(VECTOR_DRAWABLES_OPTIONS);
    return new VectorDrawablesOptionsModelImpl(vectorDrawableElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel wearAppUnbundled() {
    return getModelForProperty(WEAR_APP_UNBUNDLED);
  }
}
