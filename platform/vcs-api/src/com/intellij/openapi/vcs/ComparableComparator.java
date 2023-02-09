/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.Comparator;

public class ComparableComparator<T extends Comparable<T>> implements Comparator<T> {
  @Override
  public int compare(final T o1, final T o2) {
    return o1.compareTo(o2);
  }

  public static class Descending<T extends Comparable<T>> implements Comparator<T> {
    @Override
    public int compare(T o1, T o2) {
      return o2.compareTo(o1);
    }
  }
}
