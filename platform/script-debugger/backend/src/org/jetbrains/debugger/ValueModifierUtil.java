package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.Consumer;
import org.jetbrains.debugger.values.Value;

public final class ValueModifierUtil {
  public static ActionCallback setValue(final Variable variable, String newValue, final EvaluateContext evaluateContext, final ValueModifier modifier) {
    final ActionCallback callback = new ActionCallback();
    evaluateContext.evaluate(newValue).doWhenDone(new Consumer<Value>() {
      @Override
      public void consume(Value value) {
        modifier.setValue(variable, value, evaluateContext).notify(callback);
      }
    }).notifyWhenRejected(callback);
    return callback;
  }
}