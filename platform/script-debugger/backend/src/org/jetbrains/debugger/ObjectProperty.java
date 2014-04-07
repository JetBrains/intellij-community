package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.FunctionValue;
import org.jetbrains.debugger.values.Value;

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

  /**
   * Asynchronously evaluates property getter and returns property value
   */
  @NotNull
  AsyncResult<Value> evaluateGet(@NotNull EvaluateContext evaluateContext);
}
