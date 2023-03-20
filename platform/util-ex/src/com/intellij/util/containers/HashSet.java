// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import java.util.Collection;

/**
 * @deprecated Use {@link java.util.HashSet}
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
@Deprecated
public class HashSet<E> extends java.util.HashSet<E> {
  public HashSet() { }

  public HashSet(Collection<? extends E> collection) {
    super(collection);
  }

  public HashSet(int i) {
    super(i);
  }

  @Override
  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
