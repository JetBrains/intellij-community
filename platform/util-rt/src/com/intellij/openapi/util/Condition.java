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

/**
 * Returns {@code true} or {@code false} for the given input object.
 * <p/>
 * See {@link Conditions} for chained conditions.
 *
 * @author dsl
 */
public interface Condition<T> {
  boolean value(T t);

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
   * @see Conditions#alwaysTrue()
   */
  Condition TRUE = new Condition() {
    public boolean value(final Object object) {
      return true;
    }

    @Override
    public String toString() {
      return "Condition.TRUE";
    }
  };
  /**
   * @see Conditions#alwaysFalse()
   */
  Condition FALSE = new Condition() {
    public boolean value(final Object object) {
      return false;
    }

    @Override
    public String toString() {
      return "Condition.FALSE";
    }
  };
}