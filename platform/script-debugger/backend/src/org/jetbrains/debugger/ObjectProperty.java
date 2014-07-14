package org.jetbrains.debugger;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.FunctionValue;

/**
 * Exposes additional data if variable is a property of object and its property descriptor
 * is available.
 */
public interface ObjectProperty extends Variable {
  /**
   * @return whether property described as 'writable'
   */
  boolean isWritable();

  /**
   * @return property getter value (function or undefined) or null if not an accessor property
   */
  @Nullable
  FunctionValue getGetter();

  /**
   * @return property setter value (function or undefined) or null if not an accessor property
   */
  @Nullable
  FunctionValue getSetter();

  /**
   * @return whether property described as 'configurable'
   */
  boolean isConfigurable();

  /**
   * @return whether property described as 'enumerable'
   */
  boolean isEnumerable();
}
