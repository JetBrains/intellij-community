// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@ApiStatus.Internal
public final class Suppliers {
  public static <T> Supplier<T> of(T instance) {
    return new Suppliers.SupplierOfInstance<T>(instance);
  }

  public static <T> Supplier<T> wrap(@NotNull Supplier<? extends T> supplier) {
    try {
      return of(supplier.get());
    }
    catch (UnsupportedMethodException methodException) {
      return new UnsupportedMethodExceptionSupplier<T>(methodException);
    }
  }

  private static class UnsupportedMethodExceptionSupplier<T> implements Supplier<T>, Serializable {
    private final UnsupportedMethodException myException;

    private UnsupportedMethodExceptionSupplier(UnsupportedMethodException exception) {
      myException = new UnsupportedMethodException(exception.getMessage());
    }

    @Override
    public T get() {
      throw myException;
    }
  }

  private static class SupplierOfInstance<T> implements Supplier<T>, Serializable {
    private final T instance;

    private SupplierOfInstance(T instance) {
      this.instance = instance;
    }

    @Override
    public T get() {
      return this.instance;
    }

    public boolean equals(Object obj) {
      return obj instanceof SupplierOfInstance && this.instance.equals(((SupplierOfInstance<?>)obj).instance);
    }

    public int hashCode() {
      return this.instance.hashCode();
    }

    public String toString() {
      return "Suppliers.ofInstance(" + this.instance + ")";
    }
  }
}
