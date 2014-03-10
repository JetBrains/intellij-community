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
}