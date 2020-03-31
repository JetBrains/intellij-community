// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class PatternContext implements Comparable<PatternContext> {

  public final String myID;
  private final String myDisplayName;

  public PatternContext(@NotNull String ID, String displayName) {
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

  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String toString() {
    return myDisplayName + " (" + myID + ')';
  }
}
