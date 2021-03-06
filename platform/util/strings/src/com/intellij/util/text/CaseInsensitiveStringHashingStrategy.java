// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.Strings;
import gnu.trove.TObjectHashingStrategy;

public final class CaseInsensitiveStringHashingStrategy implements TObjectHashingStrategy<String> {
  public static final CaseInsensitiveStringHashingStrategy INSTANCE = new CaseInsensitiveStringHashingStrategy();

  @Override
  public int computeHashCode(final String s) {
    return Strings.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(final String s1, final String s2) {
    return s1.equalsIgnoreCase(s2);
  }
}
