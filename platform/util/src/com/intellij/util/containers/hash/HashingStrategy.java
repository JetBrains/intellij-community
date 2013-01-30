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

public interface HashingStrategy<T> {
  HashingStrategy<?> IDENTITY = new HashingStrategy() {
    @Override
    public int computeHashCode(Object object) {
      return System.identityHashCode(object);
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      return o1 == o2;
    }
  };

  HashingStrategy<?> CANONICAL = new HashingStrategy() {
    @Override
    public int computeHashCode(Object object) {
      return object != null ? object.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      return o1 != null ? o1.equals(o2) : o2 == null;
    }
  };


  int computeHashCode(T object);

  boolean equals(T o1, T o2);

}
