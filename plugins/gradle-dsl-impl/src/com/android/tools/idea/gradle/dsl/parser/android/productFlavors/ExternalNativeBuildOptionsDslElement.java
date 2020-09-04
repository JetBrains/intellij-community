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
package com.android.tools.idea.gradle.dsl.parser.android.productFlavors;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ExternalNativeBuildOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<ExternalNativeBuildOptionsDslElement> EXTERNAL_NATIVE_BUILD_OPTIONS =
    new PropertiesElementDescription<>(
      "externalNativeBuild", ExternalNativeBuildOptionsDslElement.class, ExternalNativeBuildOptionsDslElement::new);

  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"cmake", CMakeOptionsDslElement.CMAKE_OPTIONS},
    {"ndkBuild", NdkBuildOptionsDslElement.NDK_BUILD_OPTIONS},
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public ExternalNativeBuildOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
