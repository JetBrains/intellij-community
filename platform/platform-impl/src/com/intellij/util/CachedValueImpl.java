/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 6, 2002
 * Time: 5:41:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util;

import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CachedValueImpl<T> extends CachedValueBase<T> implements CachedValue<T> {

  private final CachedValueProvider<T> myProvider;

  public CachedValueImpl(@NotNull CachedValueProvider<T> provider) {
    super();
    myProvider = provider;
  }

  @Nullable
  public T getValue() {

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

      CachedValueProvider.Result<T> result = myProvider.compute();
      value = result == null ? null : result.getValue();

      setValue(value, result);

      return value;
    }
    finally {
      w.unlock();
    }
  }

  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }

}
