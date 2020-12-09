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

import static com.android.tools.idea.gradle.dsl.model.android.PackagingOptionsModelImpl.EXCLUDES;
import static com.android.tools.idea.gradle.dsl.model.android.PackagingOptionsModelImpl.MERGES;
import static com.android.tools.idea.gradle.dsl.model.android.PackagingOptionsModelImpl.PICK_FIRSTS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
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

public class PackagingOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAR},
    {"exclude", exactly(1), EXCLUDES, OTHER},
    {"merges", property, MERGES, VAR},
    {"merge", exactly(1), MERGES, OTHER},
    {"pickFirsts", property, PICK_FIRSTS, VAR},
    {"pickFirst", exactly(1), PICK_FIRSTS, OTHER}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAR},
    {"exclude", exactly(1), EXCLUDES, OTHER},
    {"merges", property, MERGES, VAR},
    {"merge", exactly(1), MERGES, OTHER},
    {"pickFirsts", property, PICK_FIRSTS, VAR},
    {"pickFirst", exactly(1), PICK_FIRSTS, OTHER}
  }).collect(toModelMap());
  public static final PropertiesElementDescription<PackagingOptionsDslElement> PACKAGING_OPTIONS =
    new PropertiesElementDescription<>("packagingOptions", PackagingOptionsDslElement.class, PackagingOptionsDslElement::new);

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

  public PackagingOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("exclude")) {
      addToParsedExpressionList(EXCLUDES, element);
      return;
    }

    if (property.equals("merge")) {
      addToParsedExpressionList(MERGES, element);
      return;
    }

    if (property.equals("pickFirst")) {
      addToParsedExpressionList(PICK_FIRSTS, element);
      return;
    }

    super.addParsedElement(element);
  }
}
