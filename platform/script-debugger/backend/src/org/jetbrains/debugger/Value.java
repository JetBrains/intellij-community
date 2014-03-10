package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
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

  boolean isTruncated();

  int getActualLength();

  /**
   * Asynchronously reloads object value with extended size limit
   */
  ActionCallback reloadHeavyValue();
}
