// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

/**
 * @deprecated Use {@link MultiMap#createLinked()}
 */
@Deprecated
public class LinkedMultiMap<K, V> extends MultiMap<K, V> {
  public LinkedMultiMap() {
    super(CollectionFactory.createSmallMemoryFootprintLinkedMap());
  }
}
