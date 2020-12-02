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
package com.android.tools.idea.gradle.dsl.parser.repositories;

import static com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModelImpl.NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.UrlBasedRepositoryModelImpl.ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.model.repositories.UrlBasedRepositoryModelImpl.URL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

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

public class MavenRepositoryDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<MavenRepositoryDslElement> JCENTER =
    new PropertiesElementDescription<>("jcenter", MavenRepositoryDslElement.class, MavenRepositoryDslElement::new);
  public static final PropertiesElementDescription<MavenRepositoryDslElement> MAVEN =
    new PropertiesElementDescription<>("maven", MavenRepositoryDslElement.class, MavenRepositoryDslElement::new);
  public static final PropertiesElementDescription<MavenRepositoryDslElement> MAVEN_CENTRAL =
    new PropertiesElementDescription<>("mavenCentral", MavenRepositoryDslElement.class, MavenRepositoryDslElement::new);
  public static final PropertiesElementDescription<MavenRepositoryDslElement> GOOGLE =
    new PropertiesElementDescription<>("google", MavenRepositoryDslElement.class, MavenRepositoryDslElement::new);


  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"name", property, NAME, VAR},
    {"url", property, URL, VAR},
    {"artifactUrls", atLeast(0), ARTIFACT_URLS, OTHER}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"name", property, NAME, VAR},
    {"name", exactly(1), NAME, SET},
    {"url", property, URL, VAR},
    {"url", exactly(1), URL, SET},
    {"artifactUrls", atLeast(0), ARTIFACT_URLS, OTHER}
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
  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"credentials", MavenCredentialsDslElement.CREDENTIALS}
  }).collect(toImmutableMap(data -> (String)data[0], data -> (PropertiesElementDescription)data[1]));

  @NotNull
  @Override
  protected ImmutableMap<String, PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public MavenRepositoryDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (element.getName().equals(ARTIFACT_URLS)) {
      addToParsedExpressionList(element.getName() ,element);
      return;
    }
    super.addParsedElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return false;
  }
}
