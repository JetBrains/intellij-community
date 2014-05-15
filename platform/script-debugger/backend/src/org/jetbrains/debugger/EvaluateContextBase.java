package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.debugger.values.ValueManager;

import java.util.Map;

public abstract class EvaluateContextBase<VALUE_MANAGER extends ValueManager> implements EvaluateContext {
  protected final VALUE_MANAGER valueManager;

  protected EvaluateContextBase(@NotNull VALUE_MANAGER valueManager) {
    this.valueManager = valueManager;
  }

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

  @NotNull
  @Override
  public abstract AsyncResult<Value> evaluate(@NotNull String expression, @Nullable Map<String, EvaluateContextAdditionalParameter> additionalContext);

  @NotNull
  public final VALUE_MANAGER getValueManager() {
    return valueManager;
  }

  @NotNull
  @Override
  public ActionCallback refreshOnDone(@NotNull ActionCallback result) {
    return result.doWhenDone(valueManager.getClearCachesTask());
  }
}