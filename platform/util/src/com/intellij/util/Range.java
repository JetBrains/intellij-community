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

package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public class Range<T extends Comparable<T>> {

  @NotNull private final T myFrom;
  @NotNull private final T myTo;


  public Range(@NotNull final T from, @NotNull final T to) {
    myFrom = from;
    myTo = to;
  }

  public boolean isWithin(@NotNull T object) {
    return isWithin(object, true);
  }

  public boolean isWithin(@NotNull T object, boolean includingEndpoints) {
    if (includingEndpoints) {
      return object.compareTo(myFrom) >= 0 && object.compareTo(myTo) <= 0;
    }
    return object.compareTo(myFrom) > 0 && object.compareTo(myTo) < 0;
  }

  @NotNull
  public T getFrom() {
    return myFrom;
  }

  @NotNull
  public T getTo() {
    return myTo;
  }

  @Override
  public String toString() {
    return "(" + myFrom + "," + myTo + ")";
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Range<?> range = (Range<?>)o;

    if (!myFrom.equals(range.myFrom)) return false;
    if (!myTo.equals(range.myTo)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFrom.hashCode();
    result = 31 * result + myTo.hashCode();
    return result;
  }
}
