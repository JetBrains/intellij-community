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

import static com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildFeaturesDslElement.BUILD_FEATURES;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement.BUILD_TYPE;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES;
import static com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement.COMPILE_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING;
import static com.android.tools.idea.gradle.dsl.parser.android.DefaultConfigDslElement.DEFAULT_CONFIG;
import static com.android.tools.idea.gradle.dsl.parser.android.DependenciesInfoDslElement.DEPENDENCIES_INFO;
import static com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD;
import static com.android.tools.idea.gradle.dsl.parser.android.KotlinOptionsDslElement.KOTLIN_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement.PRODUCT_FLAVOR;
import static com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS;
import static com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement.SIGNING_CONFIG;
import static com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS;
import static com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement.SOURCE_SET;
import static com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS;
import static com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS;
import static com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement.VIEW_BINDING;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildFeaturesModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.DataBindingModel;
import com.android.tools.idea.gradle.dsl.api.android.DependenciesInfoModel;
import com.android.tools.idea.gradle.dsl.api.android.DexOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.KotlinOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.SplitsModel;
import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.ViewBindingModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.BuildFeaturesDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DefaultConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DependenciesInfoDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.KotlinOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AndroidModelImpl extends GradleDslBlockModel implements AndroidModel {
  @NonNls public static final String BUILD_TOOLS_VERSION = "mBuildToolsVersion";
  @NonNls public static final String COMPILE_SDK_VERSION = "mCompileSdkVersion";
  @NonNls public static final String DEFAULT_PUBLISH_CONFIG = "mDefaultPublishConfig";
  @NonNls public static final ModelPropertyDescription DYNAMIC_FEATURES = new ModelPropertyDescription("mDynamicFeatures", MUTABLE_SET);
  @NonNls public static final String FLAVOR_DIMENSIONS = "mFlavorDimensions";
  @NonNls public static final String GENERATE_PURE_SPLITS = "mGeneratePureSplits";
  @NonNls public static final String NDK_VERSION = "mNdkVersion";
  @NonNls public static final String PUBLISH_NON_DEFAULT = "mPublishNonDefault";
  @NonNls public static final String RESOURCE_PREFIX = "mResourcePrefix";
  // TODO(xof): Add support for useLibrary

  public AndroidModelImpl(@NotNull AndroidDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public AaptOptionsModel aaptOptions() {
    AaptOptionsDslElement aaptOptionsElement = myDslElement.ensurePropertyElement(AAPT_OPTIONS);
    return new AaptOptionsModelImpl(aaptOptionsElement);
  }

  @Override
  @NotNull
  public AdbOptionsModel adbOptions() {
    AdbOptionsDslElement adbOptionsElement = myDslElement.ensurePropertyElement(ADB_OPTIONS);
    return new AdbOptionsModelImpl(adbOptionsElement);
  }

  @Override
  @NotNull
  public BuildFeaturesModel buildFeatures() {
    BuildFeaturesDslElement buildFeaturesDslElement = myDslElement.ensurePropertyElement(BUILD_FEATURES);
    return new BuildFeaturesModelImpl(buildFeaturesDslElement);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel buildToolsVersion() {
    return getModelForProperty(BUILD_TOOLS_VERSION);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel ndkVersion() {
    return getModelForProperty(NDK_VERSION);
  }

    @NotNull
  @Override
  public List<BuildTypeModel> buildTypes() {
    BuildTypesDslElement buildTypes = myDslElement.getPropertyElement(BUILD_TYPES);
    return buildTypes == null ? ImmutableList.of() : buildTypes.get();
  }

  @NotNull
  @Override
  public BuildTypeModel addBuildType(@NotNull String buildType) {
    BuildTypesDslElement buildTypes = myDslElement.ensurePropertyElement(BUILD_TYPES);
    BuildTypeDslElement buildTypeElement = buildTypes.ensureNamedPropertyElement(BUILD_TYPE, GradleNameElement.create(buildType));
    return new BuildTypeModelImpl(buildTypeElement);
  }

  @Override
  public void removeBuildType(@NotNull String buildType) {
    BuildTypesDslElement buildTypes = myDslElement.getPropertyElement(BUILD_TYPES);
    if (buildTypes != null) {
      buildTypes.removeProperty(buildType);
    }
  }

  @NotNull
  @Override
  public CompileOptionsModel compileOptions() {
    CompileOptionsDslElement element = myDslElement.ensurePropertyElement(COMPILE_OPTIONS);
    return new CompileOptionsModelImpl(element);
  }

  @NotNull
  @Override
  public ResolvedPropertyModel compileSdkVersion() {
    return getModelForProperty(COMPILE_SDK_VERSION);
  }

  @Override
  @NotNull
  public DataBindingModel dataBinding() {
    DataBindingDslElement dataBindingElement = myDslElement.ensurePropertyElement(DATA_BINDING);
    return new DataBindingModelImpl(dataBindingElement);
  }

  @Override
  @NotNull
  public ProductFlavorModel defaultConfig() {
    DefaultConfigDslElement defaultConfigElement = myDslElement.ensurePropertyElement(DEFAULT_CONFIG);
    return new ProductFlavorModelImpl(defaultConfigElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel defaultPublishConfig() {
    return getModelForProperty(DEFAULT_PUBLISH_CONFIG);
  }

  @Override
  @NotNull
  public DexOptionsModel dexOptions() {
    DexOptionsDslElement dexOptionsElement = myDslElement.ensurePropertyElement(DEX_OPTIONS);
    return new DexOptionsModelImpl(dexOptionsElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel dynamicFeatures() {
    return getModelForProperty(DYNAMIC_FEATURES);
  }

  @NotNull
  @Override
  public ExternalNativeBuildModel externalNativeBuild() {
    ExternalNativeBuildDslElement externalNativeBuildDslElement = myDslElement.ensurePropertyElement(EXTERNAL_NATIVE_BUILD);
    return new ExternalNativeBuildModelImpl(externalNativeBuildDslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel flavorDimensions() {
    return getModelForProperty(FLAVOR_DIMENSIONS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel generatePureSplits() {
    return getModelForProperty(GENERATE_PURE_SPLITS);
  }

  @Override
  @NotNull
  public KotlinOptionsModel kotlinOptions() {
    KotlinOptionsDslElement kotlinOptionsDslElement = myDslElement.ensurePropertyElement(KOTLIN_OPTIONS);
    return new KotlinOptionsModelImpl(kotlinOptionsDslElement);
  }

  @Override
  @NotNull
  public LintOptionsModel lintOptions() {
    LintOptionsDslElement lintOptionsDslElement = myDslElement.ensurePropertyElement(LINT_OPTIONS);
    return new LintOptionsModelImpl(lintOptionsDslElement);
  }

  @Override
  @NotNull
  public PackagingOptionsModel packagingOptions() {
    PackagingOptionsDslElement packagingOptionsDslElement = myDslElement.ensurePropertyElement(PACKAGING_OPTIONS);
    return new PackagingOptionsModelImpl(packagingOptionsDslElement);
  }

  @Override
  @NotNull
  public List<ProductFlavorModel> productFlavors() {
    ProductFlavorsDslElement productFlavors = myDslElement.getPropertyElement(PRODUCT_FLAVORS);
    return productFlavors == null ? ImmutableList.of() : productFlavors.get();
  }

  @Override
  @NotNull
  public ProductFlavorModel addProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors = myDslElement.ensurePropertyElement(PRODUCT_FLAVORS);
    ProductFlavorDslElement flavorElement = productFlavors.ensureNamedPropertyElement(PRODUCT_FLAVOR, GradleNameElement.create(flavor));
    return new ProductFlavorModelImpl(flavorElement);
  }

  @Override
  public void removeProductFlavor(@NotNull String flavor) {
    ProductFlavorsDslElement productFlavors = myDslElement.getPropertyElement(PRODUCT_FLAVORS);
    if (productFlavors != null) {
      productFlavors.removeProperty(flavor);
    }
  }

  @Override
  @NotNull
  public List<SigningConfigModel> signingConfigs() {
    SigningConfigsDslElement signingConfigs = myDslElement.getPropertyElement(SIGNING_CONFIGS);
    return signingConfigs == null ? ImmutableList.of() : signingConfigs.get();
  }

  @Override
  @NotNull
  public SigningConfigModel addSigningConfig(@NotNull String config) {
    SigningConfigsDslElement signingConfigs = myDslElement.ensurePropertyElementAt(SIGNING_CONFIGS, 0);
    SigningConfigDslElement configElement = signingConfigs.ensureNamedPropertyElement(SIGNING_CONFIG, GradleNameElement.create(config));
    return new SigningConfigModelImpl(configElement);
  }

  @Override
  public void removeSigningConfig(@NotNull String configName) {
    SigningConfigsDslElement signingConfig = myDslElement.getPropertyElement(SIGNING_CONFIGS);
    if (signingConfig != null) {
      signingConfig.removeProperty(configName);
    }
  }

  @Override
  @NotNull
  public List<SourceSetModel> sourceSets() {
    SourceSetsDslElement sourceSets = myDslElement.getPropertyElement(SOURCE_SETS);
    return sourceSets == null ? ImmutableList.of() : sourceSets.get();
  }

  @Override
  @NotNull
  public SourceSetModel addSourceSet(@NotNull String sourceSet) {
    SourceSetsDslElement sourceSets = myDslElement.ensurePropertyElement(SOURCE_SETS);
    SourceSetDslElement sourceSetElement = sourceSets.ensureNamedPropertyElement(SOURCE_SET, GradleNameElement.create(sourceSet));
    return new SourceSetModelImpl(sourceSetElement);
  }

  @Override
  public void removeSourceSet(@NotNull String sourceSet) {
    SourceSetsDslElement sourceSets = myDslElement.getPropertyElement(SOURCE_SETS);
    if (sourceSets != null) {
      sourceSets.removeProperty(sourceSet);
    }
  }

  @Override
  @NotNull
  public SplitsModel splits() {
    SplitsDslElement splitsDslElement = myDslElement.ensurePropertyElement(SPLITS);
    return new SplitsModelImpl(splitsDslElement);
  }

  @Override
  @NotNull
  public TestOptionsModel testOptions() {
    TestOptionsDslElement testOptionsDslElement = myDslElement.ensurePropertyElement(TEST_OPTIONS);
    return new TestOptionsModelImpl(testOptionsDslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel publishNonDefault() {
    return getModelForProperty(PUBLISH_NON_DEFAULT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resourcePrefix() {
    return getModelForProperty(RESOURCE_PREFIX);
  }

  @Override
  @NotNull
  public ViewBindingModel viewBinding() {
    ViewBindingDslElement viewBindingElement = myDslElement.ensurePropertyElement(VIEW_BINDING);
    return new ViewBindingModelImpl(viewBindingElement);
  }

  @NotNull
  @Override
  public DependenciesInfoModel dependenciesInfo() {
    DependenciesInfoDslElement dependenciesInfoElement = myDslElement.ensurePropertyElement(DEPENDENCIES_INFO);
    return new DependenciesInfoModelImpl(dependenciesInfoElement);
  }
}
