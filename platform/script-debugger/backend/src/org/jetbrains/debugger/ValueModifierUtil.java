package org.jetbrains.debugger;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.io.JsonUtil;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class ValueModifierUtil {
  private static final Pattern KEY_NOTATION_PROPERTY_NAME_PATTERN = Pattern.compile("[\\p{L}_$]+[\\d\\p{L}_$]*");

  @NotNull
  public static Promise<Void> setValue(@NotNull final Variable variable, String newValue, @NotNull final EvaluateContext evaluateContext, @NotNull final ValueModifier modifier) {
    return evaluateContext.evaluate(newValue).then(new Function<EvaluateResult, Void>() {
      @Override
      public Void fun(EvaluateResult result) {
        modifier.setValue(variable, result.value, evaluateContext);
        return null;
      }
    });
  }

  @NotNull
  public static Promise<Value> evaluateGet(@NotNull final Variable variable,
                                           @NotNull Object host,
                                           @NotNull EvaluateContext evaluateContext,
                                           @NotNull String selfName) {
    StringBuilder builder = new StringBuilder(selfName);
    appendUnquotedName(builder, variable.getName());
    return evaluateContext.evaluate(builder.toString(), Collections.singletonMap(selfName, host), false)
      .then(new Function<EvaluateResult, Value>() {
        @Override
        public Value fun(EvaluateResult result) {
          variable.setValue(result.value);
          return result.value;
        }
      });
  }

  @NotNull
  public static String propertyNamesToString(@NotNull List<String> list, boolean quotedAware) {
    StringBuilder builder = new StringBuilder();
    for (int i = list.size() - 1; i >= 0; i--) {
      String name = list.get(i);
      doAppendName(builder, name, quotedAware && (name.charAt(0) == '"' || name.charAt(0) == '\''));
    }
    return builder.toString();
  }

  public static void appendUnquotedName(@NotNull StringBuilder builder, @NotNull String name) {
    doAppendName(builder, name, false);
  }

  private static void doAppendName(@NotNull StringBuilder builder, @NotNull String name, boolean quoted) {
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