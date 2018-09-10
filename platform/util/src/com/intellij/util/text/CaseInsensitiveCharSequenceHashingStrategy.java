// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;

public class CaseInsensitiveCharSequenceHashingStrategy<T extends CharSequence> implements TObjectHashingStrategy<T> {
  public static final CaseInsensitiveCharSequenceHashingStrategy INSTANCE = new CaseInsensitiveCharSequenceHashingStrategy();
  
  @Override
  public int computeHashCode(final T s) {
    return StringUtil.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(final T s1, final T s2) {
    return StringUtil.equalsIgnoreCase(s1,s2);
  }
}
