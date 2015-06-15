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

  private final T myFrom;
  private final T myTo;


  public Range(@NotNull final T from, @NotNull final T to) {
    myFrom = from;
    myTo = to;
  }

  public boolean isWithin(T object) {
    return object.compareTo(myFrom) >= 0 && object.compareTo(myTo) <= 0;
  }


  public T getFrom() {
    return myFrom;
  }

  public T getTo() {
    return myTo;
  }

  @Override
  public String toString() {
    return "(" + myFrom + "," + myTo + ")";
  }
}
