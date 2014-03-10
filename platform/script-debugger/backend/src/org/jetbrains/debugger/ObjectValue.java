package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A compound value that has zero or more properties
 */
public interface ObjectValue extends Value {
  @Nullable
  String getClassName();

  @NotNull
  AsyncResult<ObjectPropertyData> getProperties();

  /**
   * Optionally returns unique id for this object. No two distinct objects can have the same id.
   * Lifetime of id is limited to lifetime of its {@link ValueManager} (typically corresponds
   * to the lifetime of {@link SuspendContext}.)
   */
  @Nullable
  String getRefId();
}