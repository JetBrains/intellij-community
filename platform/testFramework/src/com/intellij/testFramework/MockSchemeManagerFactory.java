/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockSchemeManagerFactory extends SchemeManagerFactory {
  private static final SchemeManager EMPTY = new EmptySchemesManager();

  @NotNull
  @Override
  protected <SCHEME extends Scheme, MUTABLE_SCHEME extends SCHEME> SchemeManager<SCHEME> create(@NotNull String directoryName,
                                                                                                @NotNull SchemeProcessor<SCHEME, ? super MUTABLE_SCHEME> processor,
                                                                                                @Nullable String presentableName,
                                                                                                @NotNull RoamingType roamingType,
                                                                                                boolean isUseOldFileNameSanitize) {
    //noinspection unchecked
    return EMPTY;
  }
}
