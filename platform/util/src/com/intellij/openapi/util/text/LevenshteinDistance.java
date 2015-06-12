/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.text;

import com.intellij.util.text.EditDistance;

/** @deprecated use {@link EditDistance} (to be removed in IDEA 17) */
@SuppressWarnings("unused")
public class LevenshteinDistance {
  public int calculateMetrics(CharSequence str1, CharSequence str2) {
    return EditDistance.levenshtein(str1, str2, true);
  }
}
