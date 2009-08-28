/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class NullableLazyKey<T,H extends UserDataHolder> extends Key<T>{
  private static final Object NULL = new Object();
  private final NullableFunction<H,T> myFunction;

  private NullableLazyKey(@NonNls String name, final NullableFunction<H, T> function) {
    super(name);
    myFunction = function;
  }

  @Nullable
  public final T getValue(H h) {
    T data = h.getUserData(this);
    if (data == null) {
      data = myFunction.fun(h);
      h.putUserData(this, data == null ? (T)NULL : data);
    }
    return data == NULL ? null : data;
  }

  public static <T,H extends UserDataHolder> NullableLazyKey<T,H> create(@NonNls String name, final NullableFunction<H, T> function) {
    return new NullableLazyKey<T,H>(name, function);
  }
}