// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
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

  @NotNull
  @Override
  @NlsSafe
  public String asString() {
    return myFullRevisionNumber;
  }

  @Override
  public int compareTo(@NotNull final VcsRevisionNumber o) {
    return Comparing.compare(myFullRevisionNumber, ((TextRevisionNumber) o).myFullRevisionNumber);
  }

  @Override
  @NlsSafe
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
