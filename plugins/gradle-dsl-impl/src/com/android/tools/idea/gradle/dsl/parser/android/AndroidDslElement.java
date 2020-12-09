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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.BUILD_TOOLS_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.COMPILE_SDK_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.DEFAULT_PUBLISH_CONFIG;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.DYNAMIC_FEATURES;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.FLAVOR_DIMENSIONS;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.GENERATE_PURE_SPLITS;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.NDK_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.PUBLISH_NON_DEFAULT;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.RESOURCE_PREFIX;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public final class AndroidDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<AndroidDslElement> ANDROID =
    new PropertiesElementDescription<>("android", AndroidDslElement.class, AndroidDslElement::new);

  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"aaptOptions", AaptOptionsDslElement.AAPT_OPTIONS},
    {"adbOptions", AdbOptionsDslElement.ADB_OPTIONS},
    {"buildFeatures", BuildFeaturesDslElement.BUILD_FEATURES},
    {"buildTypes", BuildTypesDslElement.BUILD_TYPES},
    {"compileOptions", CompileOptionsDslElement.COMPILE_OPTIONS},
    {"dataBinding", DataBindingDslElement.DATA_BINDING},
    {"defaultConfig", DefaultConfigDslElement.DEFAULT_CONFIG},
    {"dependenciesInfo", DependenciesInfoDslElement.DEPENDENCIES_INFO},
    {"dexOptions", DexOptionsDslElement.DEX_OPTIONS},
    {"externalNativeBuild", ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD},
    {"kotlinOptions", KotlinOptionsDslElement.KOTLIN_OPTIONS},
    {"lintOptions", LintOptionsDslElement.LINT_OPTIONS},
    {"packagingOptions", PackagingOptionsDslElement.PACKAGING_OPTIONS},
    {"productFlavors", ProductFlavorsDslElement.PRODUCT_FLAVORS},
    {"signingConfigs", SigningConfigsDslElement.SIGNING_CONFIGS},
    {"sourceSets", SourceSetsDslElement.SOURCE_SETS},
    {"splits", SplitsDslElement.SPLITS},
    {"testOptions", TestOptionsDslElement.TEST_OPTIONS},
    {"viewBinding", ViewBindingDslElement.VIEW_BINDING}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @NotNull
  private static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR}, // TODO(b/148657110): type handling of this is tricky
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, ADD_AS_LIST},
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"setPublishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL}, // no setResourcePrefix: not a VAR
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET}
  }).collect(toModelMap());

  @NotNull
  private static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR},
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, ADD_AS_LIST},
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"ndkVersion", exactly(1), NDK_VERSION, SET},
    {"publishNonDefault", property, PUBLISH_NON_DEFAULT, VAR},
    {"publishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL},
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET}
  }).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter.isKotlin()) {
      return ktsToModelNameMap;
    }
    else if (converter.isGroovy()) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public AndroidDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
