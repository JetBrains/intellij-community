// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

public class UpgradableRandomAccessIntContainerTests {
  public static class HashSet2BitSetTest extends RandomAccessIntContainerGenericTest {
    @Override
    RandomAccessIntContainer createInstance() {
      return new UpgradableRandomAccessIntContainer<>(50, () -> {
        return new IntHashSetAsRAIntContainer(20, 0.75f);
      }, (container) -> {
        return new BitSetAsRAIntContainer();
      });
    }
  }
}
