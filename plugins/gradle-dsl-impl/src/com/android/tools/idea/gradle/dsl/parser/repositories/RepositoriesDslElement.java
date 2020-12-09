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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class RepositoriesDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<RepositoriesDslElement> REPOSITORIES =
    new PropertiesElementDescription<>("repositories", RepositoriesDslElement.class, RepositoriesDslElement::new);

  public static final ImmutableMap<String, PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"flatDir", FlatDirRepositoryDslElement.FLAT_DIR},
    {"google", MavenRepositoryDslElement.GOOGLE},
    {"jcenter", MavenRepositoryDslElement.JCENTER},
    {"maven", MavenRepositoryDslElement.MAVEN},
    {"mavenCentral", MavenRepositoryDslElement.MAVEN_CENTRAL}
  }).collect(toImmutableMap(data -> (String)data[0], data -> (PropertiesElementDescription)data[1]));

  @NotNull
  @Override
  protected ImmutableMap<String, PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public RepositoriesDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
