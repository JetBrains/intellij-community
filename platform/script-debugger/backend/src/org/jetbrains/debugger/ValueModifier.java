package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.Value;

public interface ValueModifier {
  // expression can contains reference to another variables in current scope, so, we should evaluate it before set
  // https://youtrack.jetbrains.com/issue/WEB-2342#comment=27-512122

  // we don't worry about performance in case of simple primitive values - boolean/string/numbers,
  // it works quickly and we don't want to complicate our code and debugger SDK
  Promise<?> setValue(@NotNull Variable variable, String newValue, @NotNull EvaluateContext evaluateContext);

  Promise<?> setValue(@NotNull Variable variable, @NotNull Value newValue, @NotNull EvaluateContext evaluateContext);

  @NotNull
  Promise<Value> evaluateGet(@NotNull Variable variable, @NotNull EvaluateContext evaluateContext);
}