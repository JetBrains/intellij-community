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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.DEBUGGABLE;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.EMBED_MICRO_APP;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.JNI_DEBUGGABLE;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.MINIFY_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.PSEUDO_LOCALES_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.RENDERSCRIPT_DEBUGGABLE;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.RENDERSCRIPT_OPTIM_LEVEL;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.SHRINK_RESOURCES;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.TEST_COVERAGE_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.ZIP_ALIGN_ENABLED;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BuildTypeDslElement extends AbstractFlavorTypeDslElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<BuildTypeDslElement> BUILD_TYPE =
    new PropertiesElementDescription<>(null, BuildTypeDslElement.class, BuildTypeDslElement::new);

  @NotNull
  private static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.concat(
    AbstractFlavorTypeDslElement.ktsToModelNameMap.entrySet().stream().map(data -> new Object[]{
      data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
    }),
    Stream.of(new Object[][]{
      {"isDebuggable", property, DEBUGGABLE, VAR},
      {"isEmbedMicroApp", property, EMBED_MICRO_APP, VAR},
      {"isJniDebuggable", property, JNI_DEBUGGABLE, VAR},
      {"setJniDebuggable", exactly(1), JNI_DEBUGGABLE, SET},
      {"isMinifyEnabled", property, MINIFY_ENABLED, VAR},
      {"setMinifyEnabled", exactly(1), MINIFY_ENABLED, SET},
      {"isPseudoLocalesEnabled", property, PSEUDO_LOCALES_ENABLED, VAR},
      {"isRenderscriptDebuggable", property, RENDERSCRIPT_DEBUGGABLE, VAR},
      {"setRenderscriptDebuggable", exactly(1), RENDERSCRIPT_DEBUGGABLE, SET},
      {"renderscriptOptimLevel", property, RENDERSCRIPT_OPTIM_LEVEL, VAR},
      {"setRenderscriptOptimLevel", exactly(1), RENDERSCRIPT_OPTIM_LEVEL, SET},
      {"isShrinkResources", property, SHRINK_RESOURCES, VAR},
      {"isTestCoverageEnabled", property, TEST_COVERAGE_ENABLED, VAR},
      {"isZipAlignEnabled", property, ZIP_ALIGN_ENABLED, VAR},
      {"setZipAlignEnabled", exactly(1), ZIP_ALIGN_ENABLED, SET}
    }))
    .collect(toModelMap());

  @NotNull
  private static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.concat(
    AbstractFlavorTypeDslElement.groovyToModelNameMap.entrySet().stream().map(data -> new Object[]{
      data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
    }),
    Stream.of(new Object[][]{
      {"debuggable", property, DEBUGGABLE, VAR},
      {"debuggable", exactly(1), DEBUGGABLE, SET},
      {"embedMicroApp", property, EMBED_MICRO_APP, VAR},
      {"embedMicroApp", exactly(1), EMBED_MICRO_APP, SET},
      {"jniDebuggable", property, JNI_DEBUGGABLE, VAR},
      {"jniDebuggable", exactly(1), JNI_DEBUGGABLE, SET},
      {"minifyEnabled", property, MINIFY_ENABLED, VAR},
      {"minifyEnabled", exactly(1), MINIFY_ENABLED, SET},
      {"pseudoLocalesEnabled", property, PSEUDO_LOCALES_ENABLED, VAR},
      {"pseudoLocalesEnabled", exactly(1), PSEUDO_LOCALES_ENABLED, SET},
      {"renderscriptDebuggable", property, RENDERSCRIPT_DEBUGGABLE, VAR},
      {"renderscriptDebuggable", exactly(1), RENDERSCRIPT_DEBUGGABLE, SET},
      {"renderscriptOptimLevel", property, RENDERSCRIPT_OPTIM_LEVEL, VAR},
      {"renderscriptOptimLevel", exactly(1), RENDERSCRIPT_OPTIM_LEVEL, SET},
      {"shrinkResources", property, SHRINK_RESOURCES, VAR},
      {"shrinkResources", exactly(1), SHRINK_RESOURCES, SET},
      {"testCoverageEnabled", property, TEST_COVERAGE_ENABLED, VAR},
      {"testCoverageEnabled", exactly(1), TEST_COVERAGE_ENABLED, SET},
      {"zipAlignEnabled", property, ZIP_ALIGN_ENABLED, VAR},
      {"zipAlignEnabled", exactly(1), ZIP_ALIGN_ENABLED, SET}
    }))
    .collect(toModelMap());

  @Nullable
  private String methodName;

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

  public BuildTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // "release" and "debug" Build Type blocks can be deleted if empty
    return getName().equals("release") || getName().equals("debug");
  }

  @Override
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }
}
