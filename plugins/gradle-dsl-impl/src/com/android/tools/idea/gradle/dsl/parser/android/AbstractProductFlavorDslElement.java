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

import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.APPLICATION_ID;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.DIMENSION;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.MAX_SDK_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.MIN_SDK_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.MISSING_DIMENSION_STRATEGY;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.RENDER_SCRIPT_NDK_MODE_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.RENDER_SCRIPT_TARGET_API;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.RES_CONFIGS;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TARGET_SDK_VERSION;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TEST_APPLICATION_ID;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TEST_FUNCTIONAL_TEST;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TEST_HANDLE_PROFILING;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.VERSION_CODE;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.VERSION_NAME;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.WEAR_APP_UNBUNDLED;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for {@link ProductFlavorDslElement} and {@link DefaultConfigDslElement}
 */
public abstract class AbstractProductFlavorDslElement extends AbstractFlavorTypeDslElement {
  public static final ImmutableMap<String, PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"externalNativeBuild", ExternalNativeBuildOptionsDslElement.EXTERNAL_NATIVE_BUILD_OPTIONS},
    {"ndk", NdkOptionsDslElement.NDK_OPTIONS},
    {"vectorDrawables", VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @NotNull
  public static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> ktsToModelNameMap =
    Stream.concat(
      AbstractFlavorTypeDslElement.ktsToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
      }),
      Stream.of(new Object[][]{
        {"applicationId", property, APPLICATION_ID, VAR},
        {"setApplicationId", exactly(1), APPLICATION_ID, SET},
        {"dimension", property, DIMENSION, VAR},
        {"setDimension", exactly(1), DIMENSION, SET},
        {"maxSdkVersion", property, MAX_SDK_VERSION, VAR},
        {"maxSdkVersion", exactly(1), MAX_SDK_VERSION, SET},
        {"minSdkVersion", exactly(1), MIN_SDK_VERSION, SET},
        {"missingDimensionStrategy", atLeast(1), MISSING_DIMENSION_STRATEGY, OTHER}, // ADD
        {"renderscriptTargetApi", property, RENDER_SCRIPT_TARGET_API, VAR},
        {"renderscriptSupportModeEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_ENABLED, VAR},
        {"renderscriptSupportModeBlasEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, VAR},
        {"renderscriptNdkModeEnabled", property, RENDER_SCRIPT_NDK_MODE_ENABLED, VAR},
        {"resConfigs", atLeast(0), RES_CONFIGS, OTHER}, // FIXME(xof): actually APPENDN fails to handle resConfigs(listOf(...))
        {"resConfig", exactly(1), RES_CONFIGS, OTHER},
        {"targetSdkVersion", exactly(1), TARGET_SDK_VERSION, SET},
        {"testApplicationId", property, TEST_APPLICATION_ID, VAR},
        {"setTestApplicationId", exactly(1), TEST_APPLICATION_ID, SET},
        {"testFunctionalTest", property, TEST_FUNCTIONAL_TEST, VAR},
        {"setTestFunctionalTest", exactly(1), TEST_FUNCTIONAL_TEST, SET},
        {"testHandleProfiling", property, TEST_HANDLE_PROFILING, VAR},
        {"setTestHandleProfiling", exactly(1), TEST_HANDLE_PROFILING, SET},
        {"testInstrumentationRunner", property, TEST_INSTRUMENTATION_RUNNER, VAR},
        {"testInstrumentationRunner", exactly(1), TEST_INSTRUMENTATION_RUNNER, SET},
        // TODO(b/148657110): see the comment above manifestPlaceholders in AbstractFlavorTypeDslElement
        {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS},
        {"testInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, OTHER}, // PUTALL
        {"versionCode", property, VERSION_CODE, VAR},
        {"setVersionCode", exactly(1), VERSION_CODE, SET},
        {"versionName", property, VERSION_NAME, VAR},
        {"setVersionName", exactly(1), VERSION_NAME, SET},
        {"wearAppUnbundled", property, WEAR_APP_UNBUNDLED, VAR}
      }))
      .collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> groovyToModelNameMap =
    Stream.concat(
      AbstractFlavorTypeDslElement.groovyToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
      }),
      Stream.of(new Object[][]{
        {"applicationId", property, APPLICATION_ID, VAR},
        {"applicationId", exactly(1), APPLICATION_ID, SET},
        {"dimension", property, DIMENSION, VAR},
        {"dimension", exactly(1), DIMENSION, SET},
        {"maxSdkVersion", property, MAX_SDK_VERSION, VAR},
        {"maxSdkVersion", exactly(1), MAX_SDK_VERSION, SET},
        {"minSdkVersion", property, MIN_SDK_VERSION, VAR},
        {"minSdkVersion", exactly(1), MIN_SDK_VERSION, SET},
        {"missingDimensionStrategy", atLeast(1), MISSING_DIMENSION_STRATEGY, OTHER},
        {"renderscriptTargetApi", property, RENDER_SCRIPT_TARGET_API, VAR},
        {"renderscriptTargetApi", exactly(1), RENDER_SCRIPT_TARGET_API, SET},
        {"renderscriptSupportModeEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_ENABLED, VAR},
        {"renderscriptSupportModeEnabled", exactly(1), RENDER_SCRIPT_SUPPORT_MODE_ENABLED, SET},
        {"renderscriptSupportModeBlasEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, VAR},
        {"renderscriptSupportModeBlasEnabled", exactly(1), RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, SET},
        {"renderscriptNdkModeEnabled", property, RENDER_SCRIPT_NDK_MODE_ENABLED, VAR},
        {"renderscriptNdkModeEnabled", exactly(1), RENDER_SCRIPT_NDK_MODE_ENABLED, SET},
        {"resConfigs", atLeast(0), RES_CONFIGS, OTHER},
        {"resConfig", exactly(1), RES_CONFIGS, OTHER},
        {"targetSdkVersion", property, TARGET_SDK_VERSION, VAR},
        {"targetSdkVersion", exactly(1), TARGET_SDK_VERSION, SET},
        {"testApplicationId", property, TEST_APPLICATION_ID, VAR},
        {"testApplicationId", exactly(1), TEST_APPLICATION_ID, SET},
        {"testFunctionalTest", property, TEST_FUNCTIONAL_TEST, VAR},
        {"testFunctionalTest", exactly(1), TEST_FUNCTIONAL_TEST, SET},
        {"testHandleProfiling", property, TEST_HANDLE_PROFILING, VAR},
        {"testHandleProfiling", exactly(1), TEST_HANDLE_PROFILING, SET},
        {"testInstrumentationRunner", property, TEST_INSTRUMENTATION_RUNNER, VAR},
        {"testInstrumentationRunner", exactly(1), TEST_INSTRUMENTATION_RUNNER, SET},
        {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAR},
        {"testInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, SET},
        {"versionCode", property, VERSION_CODE, VAR},
        {"versionCode", exactly(1), VERSION_CODE, SET},
        {"versionName", property, VERSION_NAME, VAR},
        {"versionName", exactly(1), VERSION_NAME, SET},
        {"wearAppUnbundled", property, WEAR_APP_UNBUNDLED, VAR},
        {"wearAppUnbundled", exactly(1), WEAR_APP_UNBUNDLED, SET}
      }))
      .collect(toModelMap());

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

  AbstractProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    addDefaultProperty(new GradleDslExpressionMap(this, GradleNameElement.fake(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS)));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // this method has the same name in Kotlin and Groovy
    if (property.equals("missingDimensionStrategy") && element instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
      GradleDslExpressionList argumentList = methodCall.getArgumentsElement();
      ModelEffectDescription effect = new ModelEffectDescription(new ModelPropertyDescription(MISSING_DIMENSION_STRATEGY), OTHER);
      argumentList.setModelEffect(effect);
      super.addParsedElement(argumentList);
      return;
    }

    // these two methods have the same names in both currently-supported languages (Kotlin and Groovy)
    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToParsedExpressionList(RES_CONFIGS, element);
      return;
    }

    // testInstrumentationRunnerArguments has the same name in Groovy and Kotlin
    if (property.equals("testInstrumentationRunnerArguments")) {
      // This deals with references to maps.
      GradleDslElement oldElement = element;
      if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
        element = followElement((GradleDslLiteral) element);
      }
      if (!(element instanceof GradleDslExpressionMap)) {
        return;
      }

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, element.getPsiElement(), oldElement.getNameElement(), true);
        setParsedElement(testInstrumentationRunnerArgumentsElement);
      }

      testInstrumentationRunnerArgumentsElement.setPsiElement(element.getPsiElement());
      GradleDslExpressionMap elementsToAdd = (GradleDslExpressionMap)element;
      for (Map.Entry<String, GradleDslElement> entry : elementsToAdd.getPropertyElements().entrySet()) {
        testInstrumentationRunnerArgumentsElement.setParsedElement(entry.getValue());
      }
      return;
    }

    // testInstrumentationRunnerArgument has the same name in Groovy and Kotlin
    if (property.equals("testInstrumentationRunnerArgument")) {
      if (element instanceof GradleDslMethodCall) {
        element = ((GradleDslMethodCall)element).getArgumentsElement();
      }
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList gradleDslExpressionList = (GradleDslExpressionList)element;
      List<GradleDslSimpleExpression> elements = gradleDslExpressionList.getSimpleExpressions();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradleDslSimpleExpression value = elements.get(1);
      // Set the name element of the value to be the previous element.
      value.getNameElement().commitNameChange(elements.get(0).getPsiElement(), this.getDslFile().getWriter(), this);

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, GradleNameElement.create(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS));
        setParsedElement(testInstrumentationRunnerArgumentsElement);
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(value);
      return;
    }

    super.addParsedElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // defaultConfig is special in that is can be deleted if it is empty.
    return this instanceof DefaultConfigDslElement;
  }
}
