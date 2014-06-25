package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.io.JsonUtil;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class ValueModifierUtil {
  private static final Pattern KEY_NOTATION_PROPERTY_NAME_PATTERN = Pattern.compile("[\\p{L}_$]+[\\d\\p{L}_$]*");

  public static ActionCallback setValue(@NotNull final Variable variable, String newValue, @NotNull final EvaluateContext evaluateContext, @NotNull final ValueModifier modifier) {
    final ActionCallback callback = new ActionCallback();
    evaluateContext.evaluate(newValue).doWhenDone(new Consumer<Value>() {
      @Override
      public void consume(Value value) {
        modifier.setValue(variable, value, evaluateContext).notify(callback);
      }
    }).notifyWhenRejected(callback);
    return callback;
  }

  @NotNull
  public static AsyncResult<Value> evaluateGet(@NotNull final Variable variable,
                                               @NotNull EvaluateContextAdditionalParameter host,
                                               @NotNull EvaluateContext evaluateContext,
                                               @NotNull String selfName) {
    StringBuilder builder = new StringBuilder(selfName);
    appendName(builder, variable.getName(), false);
    return evaluateContext.evaluate(builder.toString(), Collections.singletonMap(selfName, host)).doWhenDone(new Consumer<Value>() {
      @Override
      public void consume(Value value) {
        variable.setValue(value);
      }
    });
  }

  public static String propertyNamesToString(List<String> list, boolean quotedAware) {
    StringBuilder builder = new StringBuilder();
    for (int i = list.size() - 1; i >= 0; i--) {
      String name = list.get(i);
      boolean quoted = quotedAware && (name.charAt(0) == '"' || name.charAt(0) == '\'');
      appendName(builder, name, quoted);
    }
    return builder.toString();
  }

  public static void appendName(@NotNull StringBuilder builder, @NotNull String name, boolean quoted) {
    boolean useKeyNotation = !quoted && KEY_NOTATION_PROPERTY_NAME_PATTERN.matcher(name).matches();
    if (builder.length() != 0) {
      builder.append(useKeyNotation ? '.' : '[');
    }
    if (useKeyNotation) {
      builder.append(name);
    }
    else {
      if (quoted) {
        builder.append(name);
      }
      else {
        JsonUtil.escape(name, builder);
      }
      builder.append(']');
    }
  }
}