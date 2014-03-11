package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

/**
 * An object that represents a VM variable value (compound or atomic).
 */
public interface Value extends EvaluateContextAdditionalParameter {
  @NotNull
  ValueType getType();

  /**
   * @return a string representation of this value
   */
  String getValueString();
}
