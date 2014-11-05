package org.jetbrains.debugger;

import org.jetbrains.debugger.values.Value;

public final class EvaluateResult {
  public final Value value;
  public final boolean wasThrown;

  public EvaluateResult(Value value, boolean wasThrown) {
    this.value = value;
    this.wasThrown = wasThrown;
  }
}