// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import java.util.Map;

/**
 * @deprecated Use {@link java.util.HashMap}
 * todo: TBR in 2020.1
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
@Deprecated
public class HashMap<K, V> extends java.util.HashMap<K, V> {
  public HashMap() { }

  public HashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public <K1 extends K, V1 extends V> HashMap(Map<? extends K1, ? extends V1> map) {
    super(map);
  }

  @Override
  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
