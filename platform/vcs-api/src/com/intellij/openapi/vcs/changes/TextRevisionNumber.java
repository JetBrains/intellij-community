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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;

public class TextRevisionNumber implements ShortVcsRevisionNumber {
  @NotNull private final String myFullRevisionNumber;
  @NotNull private final String myShortRevisionNumber;

  public TextRevisionNumber(@NotNull String fullRevisionNumber) {
    this(fullRevisionNumber, fullRevisionNumber.substring(0, Math.min(7, fullRevisionNumber.length())));
  }

  public TextRevisionNumber(@NotNull String fullRevisionNumber, @NotNull String shortRevisionNumber) {
    myFullRevisionNumber = fullRevisionNumber;
    myShortRevisionNumber = shortRevisionNumber;
  }

  @Override
  public String asString() {
    return myFullRevisionNumber;
  }

  @Override
  public int compareTo(@NotNull final VcsRevisionNumber o) {
    return Comparing.compare(myFullRevisionNumber, ((TextRevisionNumber) o).myFullRevisionNumber);
  }

  @Override
  public String toShortString() {
    return myShortRevisionNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myFullRevisionNumber.equals(((TextRevisionNumber)o).myFullRevisionNumber);
  }

  @Override
  public int hashCode() {
    return myFullRevisionNumber.hashCode();
  }
}
