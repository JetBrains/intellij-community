package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.Value;

public abstract class EvaluateContextBase implements EvaluateContext {
  @NotNull
  @Override
  public AsyncResult<Value> evaluate(@NotNull String expression) {
    return evaluate(expression, null);
  }

  @NotNull
  @Override
  public EvaluateContext withValueManager(@NotNull String objectGroup) {
    return this;
  }

  @Override
  public void releaseObjects() {
  }
}