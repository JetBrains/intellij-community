/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers.hash;


import gnu.trove.TObjectHashingStrategy;

public interface EqualityPolicy<T> {
  EqualityPolicy<?> IDENTITY = new EqualityPolicy() {

    @Override
    public int getHashCode(Object value) {
      return System.identityHashCode(value);
    }

    @Override
    public boolean isEqual(Object val1, Object val2) {
      return val1 == val2;
    }
  };

  EqualityPolicy<?> CANONICAL = new EqualityPolicy() {

    @Override
    public int getHashCode(Object value) {
      return value != null ? value.hashCode() : 0;
    }

    @Override
    public boolean isEqual(Object val1, Object val2) {
      return val1 != null ? val1.equals(val2) : val2 == null;
    }
  };
  
  class ByHashingStrategy<T> implements EqualityPolicy<T> {
    private final TObjectHashingStrategy<T> myStrategy;

    public ByHashingStrategy(TObjectHashingStrategy<T> strategy) {
      myStrategy = strategy;
    }

    @Override
    public int getHashCode(T value) {
      return myStrategy.computeHashCode(value);
    }

    @Override
    public boolean isEqual(T val1, T val2) {
      return myStrategy.equals(val1, val2);
    }
  }

  int getHashCode(T value);

  boolean isEqual(T val1, T val2);
}
