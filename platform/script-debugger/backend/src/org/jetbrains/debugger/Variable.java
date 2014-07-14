package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;

public interface Variable {
  /**
   * @return whether it is possible to read this variable
   */
  boolean isReadable();

  /**
   * Returns the value of this variable.
   *
   * @return a Value corresponding to this variable. {@code null} if the property has accessor descriptor
   * @see #isReadable()
   */
  @Nullable
  Value getValue();

  void setValue(Value value);

  @NotNull
  String getName();

  /**
   * @return whether it is possible to modify this variable
   */
  boolean isMutable();

  @Nullable
  ValueModifier getValueModifier();
}