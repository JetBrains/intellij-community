// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class NullableLazyKey<T,H extends UserDataHolder> extends Key<T>{
  private final NullableFunction<? super H, ? extends T> myFunction;

  private NullableLazyKey(@NonNls String name, final NullableFunction<? super H, ? extends T> function) {
    super(name);
    myFunction = function;
  }

  @Nullable
  public final T getValue(H h) {
    T data = h.getUserData(this);
    if (data == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      data = myFunction.fun(h);
      if (stamp.mayCacheNow()) {
        //noinspection unchecked
        h.putUserData(this, data == null ? (T)ObjectUtils.NULL : data);
      }
    }
    return data == ObjectUtils.NULL ? null : data;
  }

  public static <T,H extends UserDataHolder> NullableLazyKey<T,H> create(@NonNls String name, final NullableFunction<? super H, ? extends T> function) {
    return new NullableLazyKey<>(name, function);
  }
}