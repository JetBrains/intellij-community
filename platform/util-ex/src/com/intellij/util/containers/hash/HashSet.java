// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @deprecated Use {@link java.util.HashSet}
 */
@Deprecated(forRemoval = true)
public class HashSet<E> extends java.util.HashSet<E> {
  public HashSet() {
    super(0);
    warn();
  }

  public HashSet(int capacity) {
    super(capacity);
    warn();
  }

  public HashSet(int capacity, float loadFactor) {
    super(capacity, loadFactor);
    warn();
  }

  private static void warn() {
    Logger.getInstance(HashSet.class).warn(new Exception("Use java.util.HashSet instead"));
  }
}
