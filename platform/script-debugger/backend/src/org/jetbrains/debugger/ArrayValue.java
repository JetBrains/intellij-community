package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ArrayValue extends Value {
  int getLength();

  @NotNull
  AsyncResult<List<Value>> getValues();
}