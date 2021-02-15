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

import static com.android.tools.idea.gradle.dsl.model.android.TestOptionsModelImpl.EXECUTION;
import static com.android.tools.idea.gradle.dsl.model.android.TestOptionsModelImpl.REPORT_DIR;
import static com.android.tools.idea.gradle.dsl.model.android.TestOptionsModelImpl.RESULTS_DIR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class TestOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<TestOptionsDslElement> TEST_OPTIONS =
    new PropertiesElementDescription<>("testOptions", TestOptionsDslElement.class, TestOptionsDslElement::new);

  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"unitTests", UnitTestsDslElement.UNIT_TESTS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"reportDir", property, REPORT_DIR, VAR},
    {"resultsDir", property, RESULTS_DIR, VAR},
    {"execution", property, EXECUTION, VAR}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"reportDir", property, REPORT_DIR, VAR},
    {"reportDir", exactly(1), REPORT_DIR, SET},
    {"resultsDir", property, RESULTS_DIR, VAR},
    {"resultsDir", exactly(1), RESULTS_DIR, SET},
    {"execution", property, EXECUTION, VAR},
    {"execution", exactly(1), EXECUTION, SET}
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

  public TestOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
