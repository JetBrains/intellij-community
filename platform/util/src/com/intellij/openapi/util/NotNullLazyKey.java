/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.NotNullFunction;

/**
 * @author peter
 */
public class NotNullLazyKey<T,H extends UserDataHolder> extends Key<T>{
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("NotNullLazyKey");
  private final NotNullFunction<H,T> myFunction;

  private NotNullLazyKey(@NotNull @NonNls String name, @NotNull NotNullFunction<H, T> function) {
    super(name);
    myFunction = function;
  }

  @NotNull
  public final T getValue(@NotNull H h) {
    T data = h.getUserData(this);
    if (data == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      data = myFunction.fun(h);
      if (stamp.mayCacheNow()) {
        h.putUserData(this, data);
      }
    }
    return data;
  }

  public static <T,H extends UserDataHolder> NotNullLazyKey<T,H> create(@NonNls @NotNull String name, @NotNull NotNullFunction<H, T> function) {
    return new NotNullLazyKey<T,H>(name, function);
  }
}
