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
