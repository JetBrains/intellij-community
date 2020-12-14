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

import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class BuildTypesDslElement extends AbstractFlavorTypeCollectionDslElement implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<BuildTypesDslElement> BUILD_TYPES =
    new PropertiesElementDescription<>("buildTypes", BuildTypesDslElement.class, BuildTypesDslElement::new);

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(String name) {
    return BuildTypeDslElement.BUILD_TYPE;
  }

  @NotNull private static final List<String> implicitBuildTypes = Arrays.asList("debug", "release");

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return implicitBuildTypes.contains(name);
  }

  public BuildTypesDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @NotNull
  public List<BuildTypeModel> get() {
    List<BuildTypeModel> result = new ArrayList<>();
    for (BuildTypeDslElement dslElement : getValues(BuildTypeDslElement.class)) {
      // Filter any buildtypes that we have wrongly detected.
      if (!KNOWN_METHOD_NAMES.contains(dslElement.getName())) {
        result.add(new BuildTypeModelImpl(dslElement));
      }
    }
    return result;
  }
}
