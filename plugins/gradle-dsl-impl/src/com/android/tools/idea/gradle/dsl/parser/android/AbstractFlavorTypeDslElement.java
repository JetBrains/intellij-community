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

import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.APPLICATION_ID_SUFFIX;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.BUILD_CONFIG_FIELD;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.MANIFEST_PLACEHOLDERS;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.MATCHING_FALLBACKS;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.MULTI_DEX_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.MULTI_DEX_KEEP_FILE;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.MULTI_DEX_KEEP_PROGUARD;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.PROGUARD_FILES;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.RES_VALUE;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.SIGNING_CONFIG;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.USE_JACK;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.VERSION_NAME_SUFFIX;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link AbstractProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
    {"setApplicationIdSuffix", exactly(1), APPLICATION_ID_SUFFIX, SET},
    {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, OTHER}, // ADD: add argument list as property to Dsl
    {"consumerProguardFiles", atLeast(0), CONSUMER_PROGUARD_FILES, OTHER}, // APPENDN: append each argument
    {"setConsumerProguardFiles", exactly(1), CONSUMER_PROGUARD_FILES, SET},
    // in AGP 4.0, the manifestPlaceholders property is defined as a Java Map<String, Object>.  It is legal to use
    // assignment to set this property to e.g. mapOf("a" to "b"), but not to mutableMapOf("a" to "b") because the inferred type of the
    // mutableMapOf expression is MutableMap<String,String>, which is not compatible with (Mutable)Map<String!, Any!> (imagine something
    // later on adding an entry to the property with String key and Integer value).
    //
    // in AGP 4.1, the manifestPlaceholders property is defined as a Kotlin MutableMap<String,Any>.  It would no longer be legal to assign
    // a plain map; the assignment must be of a mutableMap.
    //
    // The DSL writer does not (as of January 2020) make use of information about which version of AGP is in use for this particular
    // project.  It is therefore difficult to support writing out an assignment to manifestPlaceholders which will work in both cases:
    // it would have to emit mutableMapOf<String,Any>(...).  This is perhaps desirable, but we don't have the general support
    // for this: properties would need to be decorated with their types  TODO(b/148657110) so that we could get both manifestPlaceholders
    //  and testInstrumentationRunnerArguments correct, and it is further complicated by setting the property through variables or extra
    // properties.
    //
    // Instead, then, we will continue parsing assignments to manifestPlaceholders permissively, but when writing we will use the setter
    // method rather than assignment.
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS},
    {"setManifestPlaceholders", exactly(1), MANIFEST_PLACEHOLDERS, OTHER}, // CLEAR + PUTALL, which is not quite the same as SET
    {"matchingFallbacks", property, MATCHING_FALLBACKS, VAL},
    {"setMatchingFallbacks", atLeast(1), MATCHING_FALLBACKS, OTHER}, // CLEAR + PUTALL, which is not quite the same as SET
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
  public static final ImmutableMap<Pair<String, Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
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

    if (property.equals("setMatchingFallbacks")) {
      // Clear the property since setMatchingFallbacks overwrites these.
      removeProperty(MATCHING_FALLBACKS);
      addToParsedExpressionList(MATCHING_FALLBACKS, element);
      return;
    }


    super.addParsedElement(element);
  }
}
