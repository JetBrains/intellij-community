// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtilRt;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use {@link com.intellij.util.containers.CollectionFactory#createCaseInsensitiveStringMap()}
 * or {@link com.intellij.util.containers.CollectionFactory#createCaseInsensitiveStringSet()} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public final class CaseInsensitiveStringHashingStrategy implements TObjectHashingStrategy<String> {
  public static final CaseInsensitiveStringHashingStrategy INSTANCE = new CaseInsensitiveStringHashingStrategy();

  @Override
  public int computeHashCode(final String s) {
    return StringUtilRt.stringHashCodeInsensitive(s);
  }

  @Override
  public boolean equals(final String s1, final String s2) {
    return s1.equalsIgnoreCase(s2);
  }
}
