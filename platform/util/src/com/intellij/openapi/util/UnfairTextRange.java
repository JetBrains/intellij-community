/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.util;

/**
 * TextRange with arbitrary offsets, not intended to be checked by {@link com.intellij.openapi.util.TextRange#assertProperRange(int, int, Object)}.
 * Please use with caution.
 *
 * @author Dmitry Avdeev
 */
public class UnfairTextRange extends TextRange {

  public UnfairTextRange(int startOffset, int endOffset) {
    super(startOffset, endOffset, false);
  }
}
