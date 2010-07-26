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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 6, 2002
 * Time: 5:41:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util;

import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.*;

public abstract class ParameterizedCachedValueImpl<T,P> extends CachedValueBase<T> implements ParameterizedCachedValue<T,P> {

  private final ParameterizedCachedValueProvider<T,P> myProvider;

  public ParameterizedCachedValueImpl(@NotNull ParameterizedCachedValueProvider<T,P> provider) {
    super();
    myProvider = provider;
  }

  @Nullable
  public T getValue(P param) {
    r.lock();

    T value;
    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }
    } finally {
      r.unlock();
    }

    w.lock();

    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }

      CachedValueProvider.Result<T> result = myProvider.compute(param);
      value = result == null ? null : result.getValue();

      setValue(value, result);

      return value;
    }
    finally {
      w.unlock();
    }
  }

  public ParameterizedCachedValueProvider<T,P> getValueProvider() {
    return myProvider;
  }
}
