// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

/**
 * @deprecated Use `() -> staticValue`
 */
@Deprecated
public final class StaticGetter<T> implements Getter<T> {
  private final T myT;

  public StaticGetter(T t) {
    myT = t;
  }

  @Override
  public T get() {
    return myT;
  }
}
