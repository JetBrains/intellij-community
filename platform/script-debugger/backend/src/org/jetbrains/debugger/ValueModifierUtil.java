package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.Consumer;

public final class ValueModifierUtil {
  public static ActionCallback setValue(final Variable variable, String newValue, EvaluateContext evaluateContext, final ValueModifier modifier) {
    final ActionCallback callback = new ActionCallback();
    evaluateContext.evaluate(newValue).doWhenDone(new Consumer<Value>() {
      @Override
      public void consume(Value value) {
        modifier.setValue(variable, value).notify(callback);
      }
    }).notifyWhenRejected(callback);
    return callback;
  }
}