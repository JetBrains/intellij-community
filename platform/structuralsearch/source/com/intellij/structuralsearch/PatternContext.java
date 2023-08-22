// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author Bas Leijdekkers
 */
public final class PatternContext {
  @NotNull
  public final String myID;
  @NotNull
  private final Supplier<String> myDisplayName;

  public PatternContext(@NonNls @NotNull String ID, @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) @NotNull String> displayName) {
    myID = ID;
    myDisplayName = displayName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PatternContext other = (PatternContext)o;
    return myID.equals(other.myID);
  }

  @Override
  public int hashCode() {
    return myID.hashCode();
  }

  @NotNull
  public String getId() {
    return myID;
  }

  public @NotNull String getDisplayName() {
    return myDisplayName.get();
  }

  @Override
  public String toString() {
    return myDisplayName.get() + " (" + myID + ')';
  }
}
