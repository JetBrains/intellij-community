package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.EvaluateContextAdditionalParameter;

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
