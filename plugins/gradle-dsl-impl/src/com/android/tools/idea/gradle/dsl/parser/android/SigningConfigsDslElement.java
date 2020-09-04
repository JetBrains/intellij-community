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

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class SigningConfigsDslElement extends GradleDslElementMap implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<SigningConfigsDslElement> SIGNING_CONFIGS =
    new PropertiesElementDescription<>("signingConfigs", SigningConfigsDslElement.class, SigningConfigsDslElement::new);

  public static final List<String> implicitSigningConfigs = Arrays.asList("debug");

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(String name) {
    return SigningConfigDslElement.SIGNING_CONFIG;
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return implicitSigningConfigs.contains(name);
  }

  public SigningConfigsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  @NotNull
  public List<SigningConfigModel> get() {
    List<SigningConfigModel> result = new ArrayList<>();
    for (SigningConfigDslElement dslElement : getValues(SigningConfigDslElement.class)) {
      result.add(new SigningConfigModelImpl(dslElement));
    }
    return result;
  }
}
