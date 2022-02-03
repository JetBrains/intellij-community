// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.text.OrdinalFormat;
import org.jetbrains.annotations.*;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;

@ApiStatus.NonExtendable
public abstract class BundleBase {
  public static final char MNEMONIC = 0x1B;
  public static final @NlsSafe String MNEMONIC_STRING = Character.toString(MNEMONIC);
  public static final boolean SHOW_LOCALIZED_MESSAGES = Boolean.getBoolean("idea.l10n");
  public static final boolean SHOW_DEFAULT_MESSAGES = Boolean.getBoolean("idea.l10n.english");
  public static final boolean SHOW_KEYS = Boolean.getBoolean("idea.l10n.keys");

  static final String L10N_MARKER = "🔅";

  private static final Logger LOG = Logger.getInstance(BundleBase.class);

  private static boolean assertOnMissedKeys;

  public static void assertOnMissedKeys(boolean doAssert) {
    assertOnMissedKeys = doAssert;
  }

  /**
   * Performs partial application of the pattern message from the bundle leaving some parameters unassigned.
   * It's expected that the message contains {@code params.length + unassignedParams} placeholders. Parameters
   * {@code {0}..{params.length-1}} will be substituted using passed params array. The remaining parameters
   * will be renumbered: {@code {params.length}} will become {@code {0}} and so on, so the resulting template
   * could be applied once more.
   *
   * @param bundle resource bundle to find the message in
   * @param key resource key
   * @param unassignedParams number of unassigned parameters
   * @param params assigned parameters
   * @return a template suitable to pass to {@link MessageFormat#format(Object)} having the specified number of placeholders left
   */
  public static @Nls String partialMessage(@NotNull ResourceBundle bundle,
                                           @NotNull String key,
                                           int unassignedParams,
                                           Object @NotNull ... params) {
    if (unassignedParams <= 0) throw new IllegalArgumentException();
    Object[] newParams = Arrays.copyOf(params, params.length + unassignedParams);
    String prefix = "#$$$TemplateParameter$$$#", suffix = "#$$$/TemplateParameter$$$#";
    for (int i = 0; i < unassignedParams; i++) {
      newParams[i + params.length] = prefix + i + suffix;
    }
    String message = message(bundle, key, newParams);
    return quotePattern(message).replace(prefix, "{").replace(suffix, "}");
  }

  private static @NlsSafe String quotePattern(String message) {
    boolean inQuotes = false;
    StringBuilder sb = new StringBuilder(message.length() + 5);
    for (int i = 0; i < message.length(); i++) {
      char c = message.charAt(i);
      boolean needToQuote = c == '{' || c == '}';
      if (needToQuote != inQuotes) {
        inQuotes = needToQuote;
        sb.append('\'');
      }
      if (c == '\'') {
        sb.append("''");
      }
      else {
        sb.append(c);
      }
    }
    if (inQuotes) {
      sb.append('\'');
    }
    return sb.toString();
  }

  public static @Nls @NotNull String message(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return messageOrDefault(bundle, key, null, params);
  }

  public static @Nls String messageOrDefault(@Nullable ResourceBundle bundle,
                                             @NotNull String key,
                                             @Nullable @Nls String defaultValue,
                                             Object @NotNull ... params) {
    if (bundle == null) return defaultValue;

    boolean resourceFound = true;

    String value;
    try {
      value = bundle.getString(key);
    }
    catch (MissingResourceException e) {
      resourceFound = false;
      value = useDefaultValue(bundle, key, defaultValue);
    }

    String result = postprocessValue(bundle, value, params);

    BiConsumer<String, String> consumer = ourTranslationConsumer;
    if (consumer != null) consumer.accept(key, result);

    if (!resourceFound) {
      return result;
    }

    if (SHOW_KEYS && SHOW_DEFAULT_MESSAGES) {
      return appendLocalizationSuffix(result, " (" + key + "=" + getDefaultMessage(bundle, key) + ")");
    }
    if (SHOW_KEYS) {
      return appendLocalizationSuffix(result, " (" + key + ")");
    }
    if (SHOW_DEFAULT_MESSAGES) {
      return appendLocalizationSuffix(result, " (" + getDefaultMessage(bundle, key) + ")");
    }
    if (SHOW_LOCALIZED_MESSAGES) {
      return appendLocalizationSuffix(result, L10N_MARKER);
    }
    return result;
  }

  public static @NotNull String getDefaultMessage(@NotNull ResourceBundle bundle, @NotNull String key) {
    try {
      Field parent = ReflectionUtil.getDeclaredField(ResourceBundle.class, "parent");
      if (parent != null) {
        Object parentBundle = parent.get(bundle);
        if (parentBundle instanceof ResourceBundle) {
          return ((ResourceBundle)parentBundle).getString(key);
        }
      }
    }
    catch (IllegalAccessException e) {
      LOG.warn("Cannot fetch default message with 'idea.l10n.english' enabled, by key '" + key + "'");
    }

    return "undefined";
  }

  private static final String[] SUFFIXES = {"</body></html>", "</html>"};

  protected static @NlsSafe @NotNull String appendLocalizationSuffix(@NotNull String result, @NotNull String suffixToAppend) {
    for (String suffix : SUFFIXES) {
      if (result.endsWith(suffix)) return result.substring(0, result.length() - suffix.length()) + L10N_MARKER + suffix;
    }
    return result + suffixToAppend;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  static @Nls @NotNull String useDefaultValue(@Nullable ResourceBundle bundle, @NotNull String key, @Nullable @Nls String defaultValue) {
    if (defaultValue != null) {
      return defaultValue;
    }

    if (assertOnMissedKeys) {
      String bundleName = bundle != null ? "(" + bundle.getBaseBundleName() + ")" : "";
      LOG.error("'" + key + "' is not found in " + bundle + bundleName);
    }

    return "!" + key + "!";
  }

  @SuppressWarnings("HardCodedStringLiteral")
  static @Nls @NotNull String postprocessValue(@NotNull ResourceBundle bundle, @NotNull @Nls String value, Object @NotNull ... params) {
    value = replaceMnemonicAmpersand(value);

    if (params.length > 0 && value.indexOf('{') >= 0) {
      Locale locale = bundle.getLocale();
      try {
        MessageFormat format = locale != null ? new MessageFormat(value, locale) : new MessageFormat(value);
        OrdinalFormat.apply(format);
        value = format.format(params);
      }
      catch (IllegalArgumentException e) {
        value = "!invalid format: `" + value + "`!";
      }
    }

    return value;
  }

  @Contract(pure = true)
  public static @NotNull String format(@NotNull String value, Object @NotNull ... params) {
    return params.length > 0 && value.indexOf('{') >= 0 ? MessageFormat.format(value, params) : value;
  }

  @Contract("null -> null; !null -> !null")
  public static @Nls String replaceMnemonicAmpersand(@Nullable @Nls String value) {
    if (value == null || value.indexOf('&') < 0 || value.indexOf(MNEMONIC) >= 0) {
      return value;
    }

    StringBuilder builder = new StringBuilder();
    boolean macMnemonic = value.contains("&&");
    int i = 0;
    while (i < value.length()) {
      char c = value.charAt(i);
      if (c == '\\') {
        if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
          builder.append('&');
          i++;
        }
        else {
          builder.append(c);
        }
      }
      else if (c == '&') {
        if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
          if (SystemInfoRt.isMac) {
            builder.append(MNEMONIC);
          }
          i++;
        }
        else if (!SystemInfoRt.isMac || !macMnemonic) {
          builder.append(MNEMONIC);
        }
      }
      else {
        builder.append(c);
      }
      i++;
    }
    @NlsSafe String result = builder.toString();
    return result;
  }

  //<editor-fold desc="Test stuff">
  private static volatile @Nullable BiConsumer<String, String> ourTranslationConsumer;

  /** The consumer is used by the "robot-server" plugin to collect key/text pairs - handy for writing UI tests for different locales. */
  @TestOnly
  public static void setTranslationConsumer(@Nullable BiConsumer<String, String> consumer) {
    ourTranslationConsumer = consumer;
  }
  //</editor-fold>
}
