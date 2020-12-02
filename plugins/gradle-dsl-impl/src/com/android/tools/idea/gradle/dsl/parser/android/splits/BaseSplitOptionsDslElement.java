/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android.splits;

import static com.android.tools.idea.gradle.dsl.model.android.splits.BaseSplitOptionsModelImpl.ENABLE;
import static com.android.tools.idea.gradle.dsl.model.android.splits.BaseSplitOptionsModelImpl.EXCLUDE;
import static com.android.tools.idea.gradle.dsl.model.android.splits.BaseSplitOptionsModelImpl.INCLUDE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public abstract class BaseSplitOptionsDslElement extends GradleDslBlockElement {

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"isEnable", property, ENABLE, VAR},
    {"exclude", property, EXCLUDE, VAL},
    {"exclude", atLeast(0), EXCLUDE, OTHER},
    {"setExclude", exactly(1), EXCLUDE, SET},
    {"include", property, INCLUDE, VAL},
    {"include", atLeast(0), INCLUDE, OTHER},
    {"setInclude", exactly(1), INCLUDE, SET},
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"enable", property, ENABLE, VAR},
    {"enable", exactly(1), ENABLE, SET},
    {"exclude", property, EXCLUDE, VAR},
    {"exclude", atLeast(0), EXCLUDE, OTHER},
    {"include", property, INCLUDE, VAR},
    {"include", atLeast(0), INCLUDE, OTHER},
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

  BaseSplitOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement blockName) {
    super(parent, blockName);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("exclude")) {
      addToParsedExpressionList(EXCLUDE, element);
      return;
    }
    if (property.equals("include")) {
      addToParsedExpressionList(INCLUDE, element);
      return;
    }
    if (property.equals("reset") && element instanceof GradleDslMethodCall) {
      addParsedResettingElement(element, INCLUDE);
      return;
    }

    super.addParsedElement(element);
  }
}
