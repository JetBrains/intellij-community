// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class NotNullLazyKey<T,H extends UserDataHolder> extends Key<T>{
  private final NotNullFunction<? super H, ? extends T> myFunction;

  private NotNullLazyKey(@NotNull @NonNls String name, @NotNull NotNullFunction<? super H, ? extends T> function) {
    super(name);
    myFunction = function;
  }

  @NotNull
  public final T getValue(@NotNull H h) {
    T data = h.getUserData(this);
    if (data == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      data = myFunction.fun(h);
      if (stamp.mayCacheNow()) {
        if (h instanceof UserDataHolderEx) {
          data = ((UserDataHolderEx)h).putUserDataIfAbsent(this, data);
        }
        else {
          h.putUserData(this, data);
        }
      }
    }
    return data;
  }

  @NotNull
  public static <T,H extends UserDataHolder> NotNullLazyKey<T,H> create(@NonNls @NotNull String name, @NotNull NotNullFunction<? super H, ? extends T> function) {
    return new NotNullLazyKey<>(name, function);
  }
}
