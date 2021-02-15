/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalNameInfo {
  /**
   * a list each element of which is the name in the external Dsl language of an element of the Dsl hierarchy.
   */
  @NotNull public final List<String> externalNameParts;
  /**
   * a tri-state boolean, indicating that this external name is used as a method (true), is used as a property (false), or
   * uncertainty (null).
   */
  @Nullable public final Boolean asMethod;
  /**
   * a boolean indicating whether this name should be emitted verbatim by the Dsl writer, or whether any hierarchical parts
   * should be quoted if necessary for the Dsl language's identifier syntax.  This should only be set to false in internal
   * construction of Dsl names, e.g. for project dependencies.
   */
  public final boolean verbatim;

  public ExternalNameInfo(@NotNull String externalName, @Nullable Boolean asMethod) {
    this.externalNameParts = Arrays.asList(externalName);
    this.asMethod = asMethod;
    this.verbatim = false;
  }

  public ExternalNameInfo(@NotNull List<String> externalNameParts, @Nullable Boolean asMethod, boolean verbatim) {
    this.externalNameParts = externalNameParts;
    this.asMethod = asMethod;
    this.verbatim = verbatim;
  }
}
