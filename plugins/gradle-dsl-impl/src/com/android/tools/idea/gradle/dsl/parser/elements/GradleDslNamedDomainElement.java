/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GradleDslNamedDomainElement {
  @NotNull String getName();

  // These methods record and return the method name (probably one of "create", "getByName", "register", "maybeCreate"...) first used to
  // access the element, if any (in Groovy, typically this is null).  Subsequent uses are assumed to be getByName() and idempotent.
  //
  // TODO(xof): consider recording all methods explicitly, so that if the first use was create() and is deleted, we can rewrite the next
  //  one to be create().
  @Nullable String getMethodName();
  void setMethodName(@Nullable String value);
}
