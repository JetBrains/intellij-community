// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.intmultimaps;

public final class HashUtils {
  private HashUtils() {
    throw new AssertionError("Not for instantiation");
  }


  /** @return hash that is acceptable as a key in {@link DurableIntToMultiIntMap} */
  public static int adjustHash(int hash) {
    if (hash == DurableIntToMultiIntMap.NO_VALUE) {
      //DurableIntToMultiIntMap doesn't allow 0 keys/values => replace 0 key with just anything !=0.
      // Key (=hash) doesn't identify value uniquely anyway, hence this replacement just adds another
      // collision -- basically, we replaced original Key.hash with our own hash, which avoids 0 at
      // the cost of slightly higher collision chances
      return -1;// anything !=0 will do
    }
    return hash;
  }
}
