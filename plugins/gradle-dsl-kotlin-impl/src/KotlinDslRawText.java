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
package com.android.tools.idea.gradle.dsl.parser.kotlin;

import com.android.tools.idea.gradle.dsl.api.ext.RawText;
import org.jetbrains.annotations.NotNull;

public class KotlinDslRawText extends RawText {
  public KotlinDslRawText(@NotNull String ktsRawText) {
    super("should never be seen", ktsRawText);
  }

  @Override
  @NotNull
  public String getGroovyText() {
    throw(new UnsupportedOperationException("Groovy text request from KotlinScript raw text"));
  }
}
