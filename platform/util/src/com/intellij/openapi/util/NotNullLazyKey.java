// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class NotNullLazyKey<T, H extends UserDataHolder> extends Key<T> {
  private final @NotNull Function<? super H, ? extends @NotNull T> myFunction;

  private NotNullLazyKey(@NotNull @NonNls String name, @NotNull Function<? super H, ? extends @NotNull T> function) {
    super(name);

    myFunction = function;
  }

  public @NotNull T getValue(@NotNull H h) {
    T data = h.getUserData(this);
    if (data == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      data = myFunction.apply(h);
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

  /**
   * @deprecated Use {@link #createLazyKey(String, Function)}
   */
  @Deprecated
  public static @NotNull <T, H extends UserDataHolder> NotNullLazyKey<T, H> create(@NonNls @NotNull String name,
                                                                                   @NotNull NotNullFunction<? super H, ? extends T> function) {
    return new NotNullLazyKey<>(name, function::fun);
  }

  public static @NotNull <T, H extends UserDataHolder> NotNullLazyKey<T, H> createLazyKey(@NonNls @NotNull String name,
                                                                                          @NotNull Function<? super H, ? extends @NotNull T> function) {
    return new NotNullLazyKey<>(name, function);
  }
}
