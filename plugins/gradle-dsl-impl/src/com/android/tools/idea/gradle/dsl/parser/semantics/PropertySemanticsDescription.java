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
package com.android.tools.idea.gradle.dsl.parser.semantics;

public enum PropertySemanticsDescription implements SemanticsDescription {
  /**
   * A read-write property
   */
  VAR,
  /**
   * A read-only property
   */
  VAL,
  /**
   * A write-only property
   */
  VWO,
  /**
   * A read-write property for parsing that should not be used for writing by the Kotlin Dsl writer, because of the complexity
   * of expressing the correct type information in a backwards-compatible way.  TODO(b/148657110) it would be nice to be able to
   * get rid of this.
   */
  VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS
}
