// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public final class AdapterProcessor<T, S> implements Processor<T> {
  private final Processor<? super S> myAdaptee;
  private final Function<? super T, ? extends S> myConversion;

  public AdapterProcessor(@NotNull Processor<? super S> adaptee, @NotNull Function<? super T, ? extends S> conversion) {
    myAdaptee = adaptee;
    myConversion = conversion;
  }

  @Override
  public boolean process(T t) {
    return myAdaptee.process(myConversion.fun(t));
  }
}
