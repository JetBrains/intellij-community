package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.Value;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class ValueModifierUtil {
  private static final Pattern KEY_NOTATION_PROPERTY_NAME_PATTERN = Pattern.compile("[\\p{L}_$]+[\\d\\p{L}_$]*");
  private static final String[] REPLACEMENT_CHARS;

  static {
    REPLACEMENT_CHARS = new String[128];
    for (int i = 0; i <= 31; i++) {
      REPLACEMENT_CHARS[i] = String.format("\\u%04x", (int)i);
    }
    REPLACEMENT_CHARS['"'] = "\\\"";
    REPLACEMENT_CHARS['\\'] = "\\\\";
    REPLACEMENT_CHARS['\t'] = "\\t";
    REPLACEMENT_CHARS['\b'] = "\\b";
    REPLACEMENT_CHARS['\n'] = "\\n";
    REPLACEMENT_CHARS['\r'] = "\\r";
    REPLACEMENT_CHARS['\f'] = "\\f";
  }

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
        escape(name, builder);
      }
      builder.append(']');
    }
  }

  public static void escape(CharSequence value, StringBuilder sb) {
    int length = value.length();
    sb.ensureCapacity(sb.capacity() + length + 2);
    sb.append('"');
    int last = 0;
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      String replacement;
      if (c < 128) {
        replacement = REPLACEMENT_CHARS[c];
        if (replacement == null) {
          continue;
        }
      }
      else if (c == '\u2028') {
        replacement = "\\u2028";
      }
      else if (c == '\u2029') {
        replacement = "\\u2029";
      }
      else {
        continue;
      }
      if (last < i) {
        sb.append(value, last, i);
      }
      sb.append(replacement);
      last = i + 1;
    }
    if (last < length) {
      sb.append(value, last, length);
    }
    sb.append('"');
  }
}