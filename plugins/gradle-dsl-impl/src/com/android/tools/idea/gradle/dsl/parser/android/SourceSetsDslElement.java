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

import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.model.android.SourceSetModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class SourceSetsDslElement extends GradleDslElementMap implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<SourceSetsDslElement> SOURCE_SETS =
    new PropertiesElementDescription<>("sourceSets", SourceSetsDslElement.class, SourceSetsDslElement::new);

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(String name) {
    return SourceSetDslElement.SOURCE_SET;
  }

  public SourceSetsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  @NotNull
  public List<SourceSetModel> get() {
    List<SourceSetModel> result = new ArrayList<>();
    for (SourceSetDslElement dslElement : getValues(SourceSetDslElement.class)) {
      result.add(new SourceSetModelImpl(dslElement));
    }
    return result;
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return true;
  }
}
