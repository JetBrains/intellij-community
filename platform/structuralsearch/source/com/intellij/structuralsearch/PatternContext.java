// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class PatternContext implements Comparable<PatternContext> {
  @NotNull
  public final String myID;
  @NotNull
  private final String myDisplayName;

  public PatternContext(@NonNls @NotNull String ID, @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String displayName) {
    myID = ID;
    myDisplayName = displayName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PatternContext other = (PatternContext)o;
    return myID.equals(other.myID) && myDisplayName.equals(other.myDisplayName);
  }

  @Override
  public int hashCode() {
    return 31 * myID.hashCode() + myDisplayName.hashCode();
  }

  @Override
  public int compareTo(@NotNull PatternContext o) {
    return myDisplayName.compareTo(o.myDisplayName);
  }

  @NotNull
  public String getId() {
    return myID;
  }

  public @NotNull String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String toString() {
    return myDisplayName + " (" + myID + ')';
  }
}
