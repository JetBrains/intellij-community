package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScriptRegExpBreakpointTarget extends BreakpointTarget {
  private final String regExp;
  public final String language;

  public ScriptRegExpBreakpointTarget(@NotNull String regExp, @Nullable String language) {
    this.regExp = regExp;
    this.language = language;
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    if (visitor instanceof ScriptRegExpSupportVisitor) {
      return ((ScriptRegExpSupportVisitor<R>)visitor).visitRegExp(this);
    }
    else {
      return visitor.visitUnknown(this);
    }
  }

  @Override
  public String toString() {
    return regExp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return regExp.equals(((ScriptRegExpBreakpointTarget)o).regExp);
  }

  @Override
  public int hashCode() {
    return regExp.hashCode();
  }
}