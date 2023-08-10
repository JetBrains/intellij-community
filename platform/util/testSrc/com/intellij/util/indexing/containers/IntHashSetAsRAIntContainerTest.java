// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import it.unimi.dsi.fastutil.Hash;

public class IntHashSetAsRAIntContainerTest extends RandomAccessIntContainerGenericTest {
  @Override
  RandomAccessIntContainer createInstance() {
    return new IntHashSetAsRAIntContainer(64, Hash.DEFAULT_LOAD_FACTOR);
  }
}
