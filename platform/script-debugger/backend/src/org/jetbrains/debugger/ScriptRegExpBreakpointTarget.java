package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

public final class ScriptRegExpBreakpointTarget extends BreakpointTarget {
  private final String regExp;

  public ScriptRegExpBreakpointTarget(@NotNull String regExp) {
    this.regExp = regExp;
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    if (visitor instanceof ScriptRegExpSupport.Visitor) {
      return ((ScriptRegExpSupport.Visitor<R>)visitor).visitRegExp(regExp);
    }
    else {
      return visitor.visitUnknown(this);
    }
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