// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class Interner<T> {
  @NotNull
  public T intern(@NotNull T name){
    throw new AbstractMethodError();
  }

  public abstract void clear();

  @NotNull
  public Set<T> getValues() {
    throw new AbstractMethodError();
  }
}
