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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import org.jetbrains.annotations.NotNull;

public class ConfigurationsDslElement extends GradleDslBlockElement implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<ConfigurationsDslElement> CONFIGURATIONS =
    new PropertiesElementDescription<>("configurations", ConfigurationsDslElement.class, ConfigurationsDslElement::new);

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(String name) {
    return ConfigurationDslElement.CONFIGURATION;
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    // TODO(xof): this is potentially quite complicated, and dependent on the state of the Dsl.  Since our main use at the moment is
    //  to create configurations to work around a *lack* of implicit creation, return false here, but we should probably account for
    //  the basic artifacts ("", "test", "androidTest") plus the built-in build types ("release", "debug") plus the built-in configuration
    //  kinds ("api", "compile", "implementation", etc.)  There is code in the Project System that compiles valid configurations, though
    //  from a complete Dsl model, rather than the partial one we have here...
    return false;
  }

  public ConfigurationsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
