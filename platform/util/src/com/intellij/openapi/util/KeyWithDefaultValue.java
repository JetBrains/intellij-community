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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public abstract class KeyWithDefaultValue<T> extends Key<T> {
  public KeyWithDefaultValue(@NotNull @NonNls String name) {
    super(name);
  }

  public abstract T getDefaultValue();

  @NotNull
  public static <T> KeyWithDefaultValue<T> create(@NotNull @NonNls String name, final T defValue) {
    return new KeyWithDefaultValue<T>(name) {
      @Override
      public T getDefaultValue() {
        return defValue;
      }
    };
  }
}
