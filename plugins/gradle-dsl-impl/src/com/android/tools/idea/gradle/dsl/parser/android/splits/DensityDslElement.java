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
package com.android.tools.idea.gradle.dsl.parser.android.splits;

import static com.android.tools.idea.gradle.dsl.model.android.splits.DensityModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class DensityDslElement extends BaseSplitOptionsDslElement {
  public static final PropertiesElementDescription<DensityDslElement> DENSITY =
    new PropertiesElementDescription<>("density", DensityDslElement.class, DensityDslElement::new);

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap =
    Stream.concat(
      BaseSplitOptionsDslElement.ktsToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().getFirst(), data.getValue().getSecond()
      }),
      Stream.of(new Object[][]{
        {"setAuto", exactly(1), AUTO, SET},
        {"compatibleScreens", atLeast(0), COMPATIBLE_SCREENS, OTHER}
      })).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> groovyToModelNameMap =
    Stream.concat(
      BaseSplitOptionsDslElement.groovyToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().getFirst(), data.getValue().getSecond()
      }),
      Stream.of(new Object[][]{
        {"auto", property, AUTO, VAR},
        {"auto", exactly(1), AUTO, SET},
        {"compatibleScreens", property, COMPATIBLE_SCREENS, VAR},
        {"compatibleScreens", atLeast(0), COMPATIBLE_SCREENS, OTHER},
      })).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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

  public DensityDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("compatibleScreens")) {
      addToParsedExpressionList(COMPATIBLE_SCREENS, element);
      return;
    }

    super.addParsedElement(element);
  }
}
