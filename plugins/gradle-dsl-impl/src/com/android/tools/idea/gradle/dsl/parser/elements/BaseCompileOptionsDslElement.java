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
package com.android.tools.idea.gradle.dsl.parser.elements;

import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.SOURCE_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for representing compileOptions block or other blocks which have sourceCompatibility / targetCompatibility fields.
 */
public abstract class BaseCompileOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"setSourceCompatibility", exactly(1), SOURCE_COMPATIBILITY, SET},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR},
    {"setTargetCompatibility", exactly(1), TARGET_COMPATIBILITY, SET}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"sourceCompatibility", exactly(1), SOURCE_COMPATIBILITY, SET},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR},
    {"targetCompatibility", exactly(1), TARGET_COMPATIBILITY, SET}
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

  protected BaseCompileOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    setParsedElement(element);
  }
}
