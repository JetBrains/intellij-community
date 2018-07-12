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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public final class CharSequenceHashingStrategy implements TObjectHashingStrategy<CharSequence> {
  public static final CharSequenceHashingStrategy CASE_SENSITIVE = new CharSequenceHashingStrategy(true);
  public static final CharSequenceHashingStrategy CASE_INSENSITIVE = new CharSequenceHashingStrategy(false);
  private final boolean myCaseSensitive;

  private CharSequenceHashingStrategy(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  @Override
  public int computeHashCode(final CharSequence chars) {
    return myCaseSensitive ? StringUtil.stringHashCode(chars) : StringUtil.stringHashCodeInsensitive(chars);
  }

  @Override
  public boolean equals(final CharSequence s1, final CharSequence s2) {
    return Comparing.equal(s1, s2, myCaseSensitive);
  }
}
