package org.jetbrains.debugger;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.FunctionValue;

/**
 * Exposes additional data if variable is a property of object and its property descriptor
 * is available.
 */
public interface ObjectProperty extends Variable {
  boolean isWritable();

  @Nullable
  FunctionValue getGetter();

  @Nullable
  FunctionValue getSetter();


  boolean isConfigurable();

  boolean isEnumerable();
}
