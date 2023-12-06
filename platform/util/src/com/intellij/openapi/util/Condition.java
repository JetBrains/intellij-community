// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * Deprecated; please use {@link Predicate} instead.
 * <p>
 * Returns {@code true} or {@code false} for the given input object.
 * <p>
 * See {@link Conditions} for chained conditions.
 */
@FunctionalInterface
public interface Condition<T> extends Predicate<T> {
  boolean value(T t);

  @Override
  default boolean test(T t) {
    return value(t);
  }

  /** @deprecated use {@link Conditions#notNull()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval  
  Condition<Object> NOT_NULL = new Condition<Object>() {
    @Override
    public boolean value(final Object object) {
      return object != null;
    }

    @Override
    public String toString() {
      return "Condition.NOT_NULL";
    }
  };

  /** @deprecated use {@link Conditions#alwaysTrue()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Condition<Object> TRUE = new Condition<Object>() {
    @Override
    public boolean value(final Object object) {
      return true;
    }

    @Override
    public String toString() {
      return "Condition.TRUE";
    }
  };

  /** @deprecated use {@link Conditions#alwaysFalse()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Condition<Object> FALSE = new Condition<Object>() {
    @Override
    public boolean value(final Object object) {
      return false;
    }

    @Override
    public String toString() {
      return "Condition.FALSE";
    }
  };
}
