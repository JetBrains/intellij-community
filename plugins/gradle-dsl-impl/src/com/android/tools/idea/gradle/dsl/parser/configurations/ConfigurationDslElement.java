/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.configurations;

import static com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationModelImpl.TRANSITIVE;
import static com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationModelImpl.VISIBLE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigurationDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<ConfigurationDslElement> CONFIGURATION =
    new PropertiesElementDescription<>(null, ConfigurationDslElement.class, ConfigurationDslElement::new);

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"isTransitive", property, TRANSITIVE, VAR},
    {"isVisible", property, VISIBLE, VAR}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"transitive", property, TRANSITIVE, VAR},
    {"transitive", exactly(1), TRANSITIVE, SET},
    {"visible", property, VISIBLE, VAR},
    {"visible", exactly(1), VISIBLE, SET}
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

  public ConfigurationDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public ConfigurationDslElement(@NotNull GradleDslElement parent, @NotNull PsiElement element, @NotNull GradleNameElement name, boolean hasBraces) {
    super(parent, name);
    setPsiElement(element);
    setHasBraces(hasBraces);
  }

  private String methodName;

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public void setMethodName(@Nullable String value) {
    methodName = value;
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return false;
  }
}
