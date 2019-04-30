// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import java.util.function.Consumer;

/**
 * Please use {@link Consumer} instead
 */
public abstract class Pass<T> implements Consumer<T> {
  public abstract void pass(T t);

  @Override
  public void accept(T t) {
    pass(t);
  }
}