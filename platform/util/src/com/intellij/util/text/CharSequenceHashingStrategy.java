// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import gnu.trove.TObjectHashingStrategy;

/**
 * @deprecated use {@link CollectionFactory#createCaseInsensitiveStringSet()} and other create*() methods
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
