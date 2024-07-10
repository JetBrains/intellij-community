// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ColorHexUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class RegistryValue {
  private static final Logger LOG = Logger.getInstance(RegistryValue.class);

  private final Registry registry;
  private final String key;
  private final @Nullable RegistryKeyDescriptor keyDescriptor;

  private final List<RegistryValueListener> listeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myChangedSinceStart;

  private String stringCachedValue;
  private Integer intCachedValue;
  private double doubleCachedValue = Double.NaN;
  private Boolean booleanCachedValue;

  RegistryValue(@NotNull Registry registry, @NonNls @NotNull String key, @Nullable RegistryKeyDescriptor keyDescriptor) {
    this.registry = registry;
    this.key = key;
    this.keyDescriptor = keyDescriptor;
  }

  public @NotNull @NlsSafe String getKey() {
    return key;
  }

  public @NotNull @NlsSafe String asString() {
    final String value = get(key, null, true);
    assert value != null : key;
    return value;
  }

  public boolean asBoolean() {
    Boolean result = booleanCachedValue;
    if (result == null) {
      result = Boolean.valueOf(get(key, "false", true));
      booleanCachedValue = result;
    }
    return result.booleanValue();
  }

  public int asInteger() {
    Integer result = intCachedValue;
    if (result == null) {
      result = calcInt();
      intCachedValue = result;
    }
    return result.intValue();
  }

  private @NotNull Integer calcInt() {
    try {
      return Integer.valueOf(get(key, "0", true));
    }
    catch (NumberFormatException e) {
      return Integer.valueOf(registry.getBundleValue(key));
    }
  }

  public boolean isMultiValue() {
    return getSelectedOption() != null;
  }

  public String[] getOptions() {
    return getOptions(registry.getBundleValue(key));
  }

  private static String[] getOptions(String value) {
    if (value != null && value.startsWith("[") && value.endsWith("]")) {
      return value.substring(1, value.length() - 1).split("\\|");
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  public @Nullable @NlsSafe String getSelectedOption() {
    // [opt1|opt2|selectedOpt*|opt4]
    String value = asString();
    int length = value.length();
    if (length < 3 || value.charAt(0) != '[' || value.charAt(length - 1) != ']') return null;
    int pos = 1;
    while (pos < length) {
      int end = value.indexOf('|', pos);
      if (end == -1) {
        end = length - 1;
      }
      if (value.charAt(end - 1) == '*') {
        return value.substring(pos, end - 1);
      }
      pos = end + 1;
    }
    return null;
  }

  public boolean isOptionEnabled(@NotNull String option) {
    return Objects.equals(getSelectedOption(), option);
  }

  public void setSelectedOption(String selected) {
    String[] options = getOptions();
    for (int i = 0; i < options.length; i++) {
      options[i] = Strings.trimEnd(options[i], "*");
      if (options[i].equals(selected)) {
        options[i] += "*";
      }
    }
    setValue("[" + String.join("|", options) + "]");
  }

  public double asDouble() {
    double result = doubleCachedValue;
    if (Double.isNaN(result)) {
      result = calcDouble();
      doubleCachedValue = result;
    }
    return result;
  }

  private double calcDouble() {
    try {
      return Double.parseDouble(get(key, "0.0", true));
    }
    catch (NumberFormatException e) {
      return Double.parseDouble(registry.getBundleValue(key));
    }
  }

  Color asColor(Color defaultValue) {
    final String s = get(key, null, true);
    if (s != null) {
      Color color = ColorHexUtil.fromHex(s, null);
      if (color != null && (key.endsWith(".color") || key.endsWith(".color.dark") || key.endsWith(".color.light"))) {
        return color;
      }
      final String[] rgb = s.split(",");
      if (rgb.length == 3) {
        try {
          return new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
        }
        catch (Exception ignored) {
        }
      }
    }
    return defaultValue;
  }

  public @NotNull @NlsSafe String getDescription() {
    if (keyDescriptor != null) {
      return keyDescriptor.getDescription();
    }
    return get(key + ".description", "", false);
  }

  boolean isRestartRequired() {
    if (keyDescriptor != null) {
      return keyDescriptor.isRestartRequired();
    }
    return Boolean.parseBoolean(get(key + ".restartRequired", "false", false));
  }

  public boolean isChangedFromDefault() {
    return isChangedFromDefault(asString(), registry);
  }

  public @Nullable String getPluginId() {
    return keyDescriptor != null ? keyDescriptor.getPluginId() : null;
  }

  final boolean isChangedFromDefault(@NotNull String newValue, @NotNull Registry registry) {
    return !newValue.equals(registry.getBundleValueOrNull(key));
  }

  protected String get(@NonNls @NotNull String key, String defaultValue, boolean isValue) throws MissingResourceException {
    if (isValue) {
      if (stringCachedValue == null) {
        stringCachedValue = _get(key, defaultValue, true);
      }
      return stringCachedValue;
    }
    return _get(key, defaultValue, false);
  }

  private @Nullable String _get(@NonNls @NotNull String key, @Nullable String defaultValue, boolean mustExistInBundle) throws MissingResourceException {
    String userValue = registry.getUserProperties().get(key);
    if (userValue != null) {
      return userValue;
    }

    String systemProperty = System.getProperty(key);
    if (systemProperty != null) {
      return systemProperty;
    }

    if (!registry.isLoaded()) {
      String message = "Attempt to load key '" + key + "' for not yet loaded registry";
      // use Disposer.isDebugMode as "we are in internal mode or inside test" flag
      if (Disposer.isDebugMode()) {
        LOG.error(message + ". Use system properties instead of registry values to configure behaviour at early startup stages.");
      }
      else {
        LOG.warn(message);
      }
    }

    if (mustExistInBundle) {
      return registry.getBundleValue(key);
    }
    else {
      String bundleValue = registry.getBundleValueOrNull(key);
      return bundleValue == null ? defaultValue : bundleValue;
    }
  }

  public void setValue(boolean value) {
    setValue(Boolean.toString(value));
  }

  public void setValue(int value) {
    setValue(Integer.toString(value));
  }

  public void setValue(String value) {
    RegistryValueListener globalValueChangeListener = registry.getValueChangeListener();
    globalValueChangeListener.beforeValueChanged(this);
    for (RegistryValueListener each : listeners) {
      each.beforeValueChanged(this);
    }
    resetCache();
    registry.getUserProperties().put(key, value);
    LOG.info("Registry value '" + key + "' has changed to '" + value + '\'');

    globalValueChangeListener.afterValueChanged(this);
    for (RegistryValueListener each : listeners) {
      each.afterValueChanged(this);
    }

    if (!isChangedFromDefault() && !isRestartRequired()) {
      registry.getUserProperties().remove(key);
    }

    myChangedSinceStart = true;
  }

  public void setValue(boolean value, @NotNull Disposable parentDisposable) {
    final boolean prev = asBoolean();
    setValue(value);
    Disposer.register(parentDisposable, () -> setValue(prev));
  }

  public void setValue(int value, @NotNull Disposable parentDisposable) {
    final int prev = asInteger();
    setValue(value);
    Disposer.register(parentDisposable, () -> setValue(prev));
  }

  public void setValue(String value, @NotNull Disposable parentDisposable) {
    final String prev = asString();
    setValue(value);
    Disposer.register(parentDisposable, () -> setValue(prev));
  }

  public boolean isChangedSinceAppStart() {
    return myChangedSinceStart;
  }

  public void resetToDefault() {
    String value = registry.getBundleValueOrNull(key);
    if (value == null) {
      registry.getUserProperties().remove(key);
    } else {
      setValue(value);
    }
  }

  public void addListener(@NotNull RegistryValueListener listener, @NotNull Disposable parent) {
    listeners.add(listener);
    Disposer.register(parent, () -> listeners.remove(listener));
  }

  public void addListener(@NotNull RegistryValueListener listener, @NotNull CoroutineScope coroutineScope) {
    listeners.add(listener);
    Objects.requireNonNull(coroutineScope.getCoroutineContext().get(Job.Key)).invokeOnCompletion(__ -> {
      listeners.remove(listener);
      return Unit.INSTANCE;
    });
  }

  @Override
  public String toString() {
    return key + "=" + asString();
  }

  void resetCache() {
    stringCachedValue = null;
    intCachedValue = null;
    doubleCachedValue = Double.NaN;
    booleanCachedValue = null;
  }

  public boolean isBoolean() {
    return isBoolean(asString());
  }

  private static boolean isBoolean(String s) {
    return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
  }
}
