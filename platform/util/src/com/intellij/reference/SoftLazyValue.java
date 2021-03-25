// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.reference;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;

/**
 * @author Dmitry Avdeev
 */
public abstract class SoftLazyValue<T> {
  private SoftReference<T> myReference;

  public T getValue() {
    T t = com.intellij.reference.SoftReference.dereference(myReference);
    if (t == null) {
      t = compute();
      myReference = new SoftReference<T>(t);
    }
    return t;
  }

  @NotNull
  protected abstract T compute();
}
