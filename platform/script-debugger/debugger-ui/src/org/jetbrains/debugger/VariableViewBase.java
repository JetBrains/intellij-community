package org.jetbrains.debugger;

import com.intellij.xdebugger.frame.XNamedValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.ValueType;

// todo remove when Firefox implementation will use SDK
public abstract class VariableViewBase extends XNamedValue {
  protected VariableViewBase(@NotNull String name) {
    super(name);
  }

  public abstract ValueType getValueType();

  public boolean isDomPropertiesValue() {
    return false;
  }
}