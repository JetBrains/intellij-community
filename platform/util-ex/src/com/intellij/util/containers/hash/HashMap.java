// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @deprecated Use {@link java.util.HashMap}
 */
@Deprecated(forRemoval = true)
public final class HashMap<K, V> extends java.util.HashMap<K, V> {
  public HashMap() {
    warn();
  }

  public HashMap(int capacity) {
    super(capacity);
    warn();
  }

  public HashMap(int capacity, float loadFactor) {
    super(capacity, loadFactor);
    warn();
  }

  private static void warn() {
    Logger.getInstance(HashMap.class).warn(new Exception("Use java.util.HashMap instead"));
  }
}
