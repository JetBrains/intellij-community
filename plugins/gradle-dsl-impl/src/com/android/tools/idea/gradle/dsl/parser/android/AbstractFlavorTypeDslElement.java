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

import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link AbstractProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String, Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap = Stream.of(new Object[][]{
    {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
    {"setApplicationIdSuffix", exactly(1), APPLICATION_ID_SUFFIX, SET},
    {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, OTHER}, // ADD: add argument list as property to Dsl
    {"consumerProguardFiles", atLeast(0), CONSUMER_PROGUARD_FILES, OTHER}, // APPENDN: append each argument
    {"setConsumerProguardFiles", exactly(1), CONSUMER_PROGUARD_FILES, SET},
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR},
    {"matchingFallbacks", property, MATCHING_FALLBACKS, VAR},
    {"multiDexEnabled", property, MULTI_DEX_ENABLED, VAR},
    {"setMultiDexEnabled", exactly(1), MULTI_DEX_ENABLED, SET},
    {"multiDexKeepFile", property, MULTI_DEX_KEEP_FILE, VAR},
    {"multiDexKeepProguard", property, MULTI_DEX_KEEP_PROGUARD, VAR},
    {"proguardFiles", atLeast(0), PROGUARD_FILES, OTHER},
    {"setProguardFiles", exactly(1), PROGUARD_FILES, SET},
    {"resValue", exactly(3), RES_VALUE, OTHER},
    {"signingConfig", property, SIGNING_CONFIG, VAR},
    {"useJack", property, USE_JACK, VAR}, // actually deprecated / nonexistent
    {"useJack", exactly(1), USE_JACK, SET}, // see above
    {"versionNameSuffix", property, VERSION_NAME_SUFFIX, VAR},
    {"setVersionNameSuffix", exactly(1), VERSION_NAME_SUFFIX, SET}
  })
    .collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String, Integer>, Pair<String, SemanticsDescription>> groovyToModelNameMap = Stream.of(new Object[][]{
    {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
    {"applicationIdSuffix", exactly(1), APPLICATION_ID_SUFFIX, SET},
    {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, OTHER},
    {"consumerProguardFiles", atLeast(0), CONSUMER_PROGUARD_FILES, OTHER},
    {"consumerProguardFiles", property, CONSUMER_PROGUARD_FILES, VAR},
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR},
    {"manifestPlaceholders", exactly(1), MANIFEST_PLACEHOLDERS, SET},
    {"matchingFallbacks", property, MATCHING_FALLBACKS, VAR},
    {"multiDexEnabled", property, MULTI_DEX_ENABLED, VAR},
    {"multiDexEnabled", exactly(1), MULTI_DEX_ENABLED, SET},
    {"multiDexKeepFile", exactly(1), MULTI_DEX_KEEP_FILE, SET},
    {"multiDexKeepProguard", exactly(1), MULTI_DEX_KEEP_PROGUARD, SET},
    {"proguardFiles", atLeast(0), PROGUARD_FILES, OTHER},
    {"proguardFiles", property, PROGUARD_FILES, VAR},
    {"resValue", exactly(3), RES_VALUE, OTHER},
    {"signingConfig", property, SIGNING_CONFIG, VAR},
    {"signingConfig", exactly(1), SIGNING_CONFIG, SET},
    {"useJack", property, USE_JACK, VAR},
    {"useJack", exactly(1), USE_JACK, SET},
    {"versionNameSuffix", property, VERSION_NAME_SUFFIX, VAR},
    {"versionNameSuffix", exactly(1), VERSION_NAME_SUFFIX, SET}
  })
    .collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, Pair<String, SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    addDefaultProperty(new GradleDslExpressionMap(this, GradleNameElement.fake(MANIFEST_PLACEHOLDERS)));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // setProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setProguardFiles")) {
      // Clear the property since setProguardFiles overwrites these.
      removeProperty(PROGUARD_FILES);
      addToParsedExpressionList(PROGUARD_FILES, element);
      return;
    }

    // setConsumerProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setConsumerProguardFiles")) {
      removeProperty(CONSUMER_PROGUARD_FILES);
      addToParsedExpressionList(CONSUMER_PROGUARD_FILES, element);
      return;
    }

    // proguardFiles and proguardFile have the same name in Groovy and Kotlin
    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToParsedExpressionList(PROGUARD_FILES, element);
      return;
    }

    // consumerProguardFiles and consumerProguardFile have the same name in Groovy and Kotlin
    if (property.equals("consumerProguardFiles") || property.equals("consumerProguardFile")) {
      addToParsedExpressionList(CONSUMER_PROGUARD_FILES, element);
      return;
    }

    super.addParsedElement(element);
  }
}
