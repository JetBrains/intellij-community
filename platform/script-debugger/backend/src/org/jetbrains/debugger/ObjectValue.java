package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.Nullable;

/**
 * A compound JsValue that has zero or more properties. Note that JavaScript {@code null}
 * value while officially being 'object' in the SDK is represented as a plain {@link Value}.
 */
public interface ObjectValue extends Value {
  @Nullable
  String getClassName();

  AsyncResult<ObjectPropertyData> getProperties();

  @Nullable
  AsyncResult<FunctionValue> asFunction();

  /**
   * Optionally returns unique id for this object. No two distinct objects can have the same id.
   * Lifetime of id is limited to lifetime of its {@link ValueLoader} (typically corresponds
   * to the lifetime of {@link SuspendContext}.)
   */
  @Nullable
  String getRefId();
}