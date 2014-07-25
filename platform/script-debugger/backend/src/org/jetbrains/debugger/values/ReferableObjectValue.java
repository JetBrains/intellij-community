package org.jetbrains.debugger.values;

import org.jetbrains.annotations.Nullable;

// todo remove
public interface ReferableObjectValue extends ObjectValue {
  /**
   * Optionally returns unique id for this object. No two distinct objects can have the same id.
   * Lifetime of id is limited to lifetime of its {@link ValueManager} (typically corresponds
   * to the lifetime of {@link org.jetbrains.debugger.SuspendContext}.)
   */
  @Nullable
  String getRefId();
}