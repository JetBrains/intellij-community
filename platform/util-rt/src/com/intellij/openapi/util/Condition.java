/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.annotations.ApiStatus;

/**
 * Deprecated. Please use {@link java.util.function.Predicate} instead.
 *
 * Returns {@code true} or {@code false} for the given input object.
 * <p/>
 * See {@link Conditions} for chained conditions.
 */
public interface Condition<T> {
  boolean value(T t);

  /**
   * @deprecated use {@link Conditions#notNull()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval  
  Condition<Object> NOT_NULL = new Condition<Object>() {
    public boolean value(final Object object) {
      return object != null;
    }

    @Override
    public String toString() {
      return "Condition.NOT_NULL";
    }
  };

  /**
   * @deprecated use {@link Conditions#alwaysTrue()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Condition<Object> TRUE = new Condition<Object>() {
    public boolean value(final Object object) {
      return true;
    }

    @Override
    public String toString() {
      return "Condition.TRUE";
    }
  };
  /**
   * @deprecated use {@link Conditions#alwaysFalse()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Condition<Object> FALSE = new Condition<Object>() {
    public boolean value(final Object object) {
      return false;
    }

    @Override
    public String toString() {
      return "Condition.FALSE";
    }
  };
}