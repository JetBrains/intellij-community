/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public final class CharSequenceHashingStrategy implements TObjectHashingStrategy<CharSequence> {
  private static final int COMPARISON_THRESHOLD = 5;

  @Override
  public int computeHashCode(final CharSequence chars) {
    return StringUtil.stringHashCode(chars);
  }

  @Override
  public boolean equals(final CharSequence s1, final CharSequence s2) {
    if(s1 == null || s2 == null) return false;
    if(s1 == s2) return true;
    int len = s1.length();
    if (len != s2.length()) return false;

    if (len > COMPARISON_THRESHOLD && s1 instanceof String && s2 instanceof String) {
      return s1.equals(s2);
    }

    for (int i = 0; i < len; i++) {
      if (s1.charAt(i) != s2.charAt(i)) return false;
    }
    return true;
  }
}
