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
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
public interface Computable <T> {

  T compute();

  class PredefinedValueComputable<T> implements Computable<T> {

    private final T myValue;

    public PredefinedValueComputable(@Nullable T value) {
      myValue = value;
    }

    @Override
    public T compute() {
      return myValue;
    }
  }

  /**
   * @deprecated Use {@link NotNullLazyValue}::getValue instead
   */
  @Deprecated
  abstract class NotNullCachedComputable<T> implements NotNullComputable<T> {
    private T myValue;

    @NotNull
    protected abstract T internalCompute();

    @NotNull
    @Override
    public final T compute() {
      if (myValue == null) {
        myValue = internalCompute();
      }
      return myValue;
    }
  }
}
