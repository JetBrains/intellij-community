// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.dictionaries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to store dictionaries inside FUSDictionariesService. Semantically equivalent to Optional<FUSDictionary>.
 * With this wrapper we can store either a real dictionary, or a special value indicating that dictionary is deprecated.
 * We store deprecated dictionaries to avoid frequent http requests for loading them.
 */
class FUSCachedDictionary {
  static final FUSCachedDictionary DEPRECATED = new FUSCachedDictionary();

  private final FUSDictionary myDictionary;

  FUSCachedDictionary(@NotNull FUSDictionary dictionary) {
    myDictionary = dictionary;
  }

  private FUSCachedDictionary() {
    myDictionary = null;
  }

  @Nullable
  FUSDictionary getDictionary() {
    return myDictionary;
  }
}
