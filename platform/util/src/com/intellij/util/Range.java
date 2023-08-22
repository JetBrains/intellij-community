// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public class Range<T extends Comparable<? super T>> {

  private final @NotNull T myFrom;
  private final @NotNull T myTo;


  public Range(final @NotNull T from, final @NotNull T to) {
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

  public @NotNull T getFrom() {
    return myFrom;
  }

  public @NotNull T getTo() {
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
