// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.containers.BiDirectionalEnumerator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

public class InMemoryDataEnumerator<Data> implements DataEnumeratorEx<Data> {
  private final BiDirectionalEnumerator<Data> myEnumerator = new BiDirectionalEnumerator<>(16, ContainerUtil.canonicalStrategy());

  @Override
  public int tryEnumerate(Data name) {
    return myEnumerator.contains(name) ? myEnumerator.get(name) : 0;
  }

  @Override
  public int enumerate(@Nullable Data value) {
    return myEnumerator.enumerate(value);
  }

  @Nullable
  @Override
  public Data valueOf(int idx) {
    return myEnumerator.getValue(idx);
  }
}
