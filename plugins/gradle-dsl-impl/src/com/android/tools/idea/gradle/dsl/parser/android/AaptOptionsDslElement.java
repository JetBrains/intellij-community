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

import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.ADDITIONAL_PARAMETERS;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.CRUNCHER_ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.CRUNCHER_PROCESSES;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.FAIL_ON_MISSING_CONFIG_ENTRY;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.IGNORE_ASSETS;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.NAMESPACED;
import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.NO_COMPRESS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

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

public class AaptOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, ADD_AS_LIST},
    {"cruncherEnabled", property, CRUNCHER_ENABLED, VAR},
    {"cruncherProcesses", property, CRUNCHER_PROCESSES, VAR},
    {"failOnMissingConfigEntry", property, FAIL_ON_MISSING_CONFIG_ENTRY, VAR},
    {"failOnMissingConfigEntry", exactly(1), FAIL_ON_MISSING_CONFIG_ENTRY, SET},
    {"ignoreAssetsPattern", property, IGNORE_ASSETS, VAR},
    {"ignoreAssets", property, IGNORE_ASSETS, VAR},
    {"noCompress", property, NO_COMPRESS, VAL},
    {"noCompress", atLeast(0), NO_COMPRESS, ADD_AS_LIST},
    {"setNoCompress", exactly(1), NO_COMPRESS, SET}, // actually there are more setNoCompress() methods than just the pure setter
    {"namespaced", property, NAMESPACED, VAR},
    {"namespaced", exactly(1), NAMESPACED, SET}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, ADD_AS_LIST},
    {"cruncherEnabled", property, CRUNCHER_ENABLED, VAR},
    {"cruncherEnabled", exactly(1), CRUNCHER_ENABLED, SET},
    {"cruncherProcesses", property, CRUNCHER_PROCESSES, VAR},
    {"cruncherProcesses", exactly(1), CRUNCHER_PROCESSES, SET},
    {"failOnMissingConfigEntry", property, FAIL_ON_MISSING_CONFIG_ENTRY, VAR},
    {"failOnMissingConfigEntry", exactly(1), FAIL_ON_MISSING_CONFIG_ENTRY, SET},
    {"ignoreAssetsPattern", property, IGNORE_ASSETS, VAR},
    {"ignoreAssetsPattern", exactly(1), IGNORE_ASSETS, SET},
    {"ignoreAssets", property, IGNORE_ASSETS, VAR},
    {"ignoreAssets", exactly(1), IGNORE_ASSETS, SET},
    {"noCompress", property, NO_COMPRESS, VAR},
    {"noCompress", atLeast(0), NO_COMPRESS, ADD_AS_LIST},
    {"namespaced", property, NAMESPACED, VAR},
    {"namespaced", exactly(1), NAMESPACED, SET}
  }).collect(toModelMap());
  public static final PropertiesElementDescription<AaptOptionsDslElement> AAPT_OPTIONS =
    new PropertiesElementDescription<>("aaptOptions", AaptOptionsDslElement.class, AaptOptionsDslElement::new);

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

  public AaptOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
