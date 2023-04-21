// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import java.util.function.BooleanSupplier;

/**
 * @deprecated Use {@link java.util.function.BooleanSupplier} instead
 */
@Deprecated
@FunctionalInterface
public interface BooleanGetter extends BooleanSupplier {
  BooleanGetter TRUE = () -> true;

  BooleanGetter FALSE = () -> false;

  boolean get();

  @Override
  default boolean getAsBoolean() {
    return get();
  }
}
