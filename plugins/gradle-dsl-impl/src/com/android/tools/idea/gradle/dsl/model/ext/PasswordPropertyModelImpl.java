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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.*;

public class PasswordPropertyModelImpl extends MultiTypePropertyModelImpl<PasswordType> implements PasswordPropertyModel {
  @NonNls private static final String SYSTEM_GETENV = "System.getenv";
  @NonNls private static final String SYSTEM_CONSOLE_READ_LINE = "System.console().readLine";

  @NotNull private static PropertyTransform ENV_VAR_TRANSFORM = new SingleArgumentMethodTransform(SYSTEM_GETENV);
  @NotNull private static PropertyTransform SYS_CON_TRANSFORM = new SingleArgumentMethodTransform(SYSTEM_CONSOLE_READ_LINE);

  public PasswordPropertyModelImpl(@NotNull GradleDslElement element) {
    super(PLAIN_TEXT, element, createMap());
  }

  public PasswordPropertyModelImpl(@NotNull GradleDslElement holder,
                                   @NotNull PropertyType type,
                                   @NotNull String name) {
    super(PLAIN_TEXT, holder, type, name, createMap());
  }

  private static LinkedHashMap<PasswordType, PropertyTransform> createMap() {
    LinkedHashMap<PasswordType, PropertyTransform> transforms = new LinkedHashMap<>();
    transforms.put(ENVIRONMENT_VARIABLE, ENV_VAR_TRANSFORM);
    transforms.put(CONSOLE_READ, SYS_CON_TRANSFORM);
    // PLAIN_TEXT uses the DEFAULT_TRANSFORM so doesn't need to be added.
    return transforms;
  }
}
