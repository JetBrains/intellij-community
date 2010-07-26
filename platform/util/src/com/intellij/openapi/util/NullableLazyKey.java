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

import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class NullableLazyKey<T,H extends UserDataHolder> extends Key<T>{
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
      h.putUserData(this, data == null ? (T)ObjectUtils.NULL : data);
    }
    return data == ObjectUtils.NULL ? null : data;
  }

  public static <T,H extends UserDataHolder> NullableLazyKey<T,H> create(@NonNls String name, final NullableFunction<H, T> function) {
    return new NullableLazyKey<T,H>(name, function);
  }
}