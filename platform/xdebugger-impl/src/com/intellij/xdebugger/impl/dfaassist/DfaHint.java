// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dfaassist;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Representation of DfaAssist hint
 */
@ApiStatus.Experimental
public enum DfaHint {
  NONE(null), ANY_VALUE(null, true), TRUE("= true", true), FALSE("= false", true), NULL("= null", true),
  NPE("[NullPointerException]"), NULL_AS_NOT_NULL("[Null passed where not-null expected]"), CCE("[ClassCastException]"),
  ASE("[ArrayStoreException]"), AIOOBE("[ArrayIndexOutOfBoundsException]"), FAIL("[Method will fail]", true);

  private final String myTitle;
  private final boolean myValue;

  DfaHint(String title) {
    this(title, false);
  }

  DfaHint(String title, boolean value) {
    myTitle = title;
    myValue = value;
  }

  /**
   * @return hint text to display in the editor. May return null if the hint is technical (not intended to be displayed)
   */
  public String getTitle() {
    return myTitle;
  }

  /**
   * Merges two states for the same code location
   *
   * @return merged state
   */
  public @NotNull DfaHint merge(@NotNull DfaHint other) {
    if (other == this) return this;
    if (this.myValue && other.myValue) return ANY_VALUE;
    if (this.myValue) return other;
    if (other.myValue) return this;
    if (this == CCE && other == NPE || this == NPE && other == CCE) return NPE;
    return NONE;
  }
}
