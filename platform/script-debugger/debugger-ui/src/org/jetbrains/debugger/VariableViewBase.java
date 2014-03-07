package org.jetbrains.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XNamedValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class VariableViewBase extends XNamedValue {
  protected VariableViewBase(@NotNull String name) {
    super(name);
  }

  public abstract ValueType getValueType();

  public boolean isDomPropertiesValue() {
    return false;
  }

  @Nullable
  protected Icon getIcon() {
    ValueType type = getValueType();
    switch (type) {
      case FUNCTION:
        return AllIcons.Nodes.Function;
      case ARRAY:
        return AllIcons.Debugger.Db_array;
      default:
        return type.isObjectType() ? AllIcons.Debugger.Value : AllIcons.Debugger.Db_primitive;
    }
  }
}