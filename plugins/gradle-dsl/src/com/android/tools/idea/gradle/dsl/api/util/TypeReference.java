// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.api.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeReference<T> {

  protected final Type myType;

  protected TypeReference() {
    Type superClass = getClass().getGenericSuperclass();
    if (superClass instanceof Class) {
      throw new IllegalStateException("No type parameter given!");
    }
    myType = ((ParameterizedType) superClass).getActualTypeArguments()[0];
  }

  public Type getType() {
    return myType;
  }

  @SuppressWarnings("unchecked")
  public T castTo(@NotNull Object o) {
    Class<T> rawType = myType instanceof Class<?> ? (Class<T>) myType : (Class<T>) ((ParameterizedType) myType).getRawType();
    if (rawType.isAssignableFrom(o.getClass())) {
      return rawType.cast(o);
    }

    return null;
  }
}
