// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ColorHexUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.MissingResourceException;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class RegistryValue {
  private final Registry myRegistry;
  private final String myKey;
  @Nullable private final RegistryKeyDescriptor myKeyDescriptor;

  private final List<RegistryValueListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myChangedSinceStart;

  private String myStringCachedValue;
  private Integer myIntCachedValue;
  private double myDoubleCachedValue = Double.NaN;
  private Boolean myBooleanCachedValue;
  private static final Logger LOG = Logger.getInstance(RegistryValue.class);

  RegistryValue(@NotNull Registry registry, @NonNls @NotNull String key, @Nullable RegistryKeyDescriptor keyDescriptor) {
    myRegistry = registry;
    myKey = key;
    myKeyDescriptor = keyDescriptor;
  }

  @NotNull
  public @NlsSafe String getKey() {
    return myKey;
  }


  @NotNull
  public @NlsSafe String asString() {
    final String value = get(myKey, null, true);
    assert value != null : myKey;
    return value;
  }

  public boolean asBoolean() {
    Boolean result = myBooleanCachedValue;
    if (result == null) {
      myBooleanCachedValue = result = calcBoolean();
    }
    return result.booleanValue();
  }

  @NotNull
  private Boolean calcBoolean() {
    return Boolean.valueOf(get(myKey, "false", true));
  }

  public int asInteger() {
    Integer result = myIntCachedValue;
    if (result == null) {
      result = calcInt();
      myIntCachedValue = result;
    }
    return result.intValue();
  }

  @NotNull
  private Integer calcInt() {
    try {
      return Integer.valueOf(get(myKey, "0", true));
    }
    catch (NumberFormatException e) {
      String bundleValue = Registry.getInstance().getBundleValue(myKey, true);
      assert bundleValue != null;
      return Integer.valueOf(bundleValue);
    }
  }

  public boolean isMultiValue() {
    return getSelectedOption() != null;
  }

  public String[] getOptions() {
    return getOptions(Registry.getInstance().getBundleValue(myKey, true));
  }

  private static String[] getOptions(String value) {
    if (value != null && value.startsWith("[") && value.endsWith("]")) {
      return value.substring(1, value.length() - 1).split("\\|");
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public @NlsSafe String getSelectedOption() {
    for (String option : getOptions(asString())) {
      if (option.endsWith("*")) {
        return StringUtil.trimEnd(option, "*");
      }
    }
    return null;
  }

  public boolean isOptionEnabled(@NotNull String option) {
    return StringUtil.equals(getSelectedOption(), option);
  }

  public void setSelectedOption(String selected) {
    String[] options = getOptions();
    for (int i = 0; i < options.length; i++) {
      options[i] = Strings.trimEnd(options[i], "*");
      if (options[i].equals(selected)) {
        options[i] += "*";
      }
    }
    setValue("[" + StringUtil.join(options,"|") + "]");
  }

  public double asDouble() {
    double result = myDoubleCachedValue;
    if (Double.isNaN(result)) {
      result = calcDouble();
      myDoubleCachedValue = result;
    }
    return result;
  }

  private double calcDouble() {
    try {
      return Double.parseDouble(get(myKey, "0.0", true));
    }
    catch (NumberFormatException e) {
      String bundleValue = Registry.getInstance().getBundleValue(myKey, true);
      assert bundleValue != null;
      return Double.parseDouble(bundleValue);
    }
  }

  Color asColor(Color defaultValue) {
    final String s = get(myKey, null, true);
    if (s != null) {
      Color color = ColorHexUtil.fromHex(s, null);
      if (color != null && myKey.contains("color")) {
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

  @NotNull
  public @NlsSafe String getDescription() {
    if (myKeyDescriptor != null) {
      return myKeyDescriptor.getDescription();
    }
    return get(myKey + ".description", "", false);
  }

  boolean isRestartRequired() {
    if (myKeyDescriptor != null) {
      return myKeyDescriptor.isRestartRequired();
    }
    return Boolean.parseBoolean(get(myKey + ".restartRequired", "false", false));
  }

  public boolean isChangedFromDefault() {
    return isChangedFromDefault(asString(), Registry.getInstance());
  }

  @Nullable
  public String getPluginId() {
    return myKeyDescriptor != null ? myKeyDescriptor.getPluginId() : null;
  }

  final boolean isChangedFromDefault(@NotNull String newValue, @NotNull Registry registry) {
    return !newValue.equals(registry.getBundleValue(myKey, false));
  }

  protected String get(@NonNls @NotNull String key, String defaultValue, boolean isValue) throws MissingResourceException {
    if (isValue) {
      if (myStringCachedValue == null) {
        myStringCachedValue = _get(key, defaultValue, true);
      }
      return myStringCachedValue;
    }
    return _get(key, defaultValue, false);
  }

  @Nullable
  private String _get(@NonNls @NotNull String key, @Nullable String defaultValue, boolean mustExistInBundle) throws MissingResourceException {
    String userValue = myRegistry.getUserProperties().get(key);
    if (userValue != null) {
      return userValue;
    }

    String systemProperty = System.getProperty(key);
    if (systemProperty != null) {
      return systemProperty;
    }

    if (!myRegistry.isLoaded()) {
      String message = "Attempt to load key '" + key + "' for not yet loaded registry";
      // use Disposer.isDebugMode as "we are in internal mode or inside test" flag
      if (Disposer.isDebugMode()) {
        LOG.warn(message + ". Use system properties instead of registry values to configure behaviour at early startup stages.",
                 new Throwable());
      }
      else {
        LOG.warn(message);
      }
    }

    String bundleValue = Registry.getInstance().getBundleValue(key, mustExistInBundle);
    return bundleValue == null ? defaultValue : bundleValue;
  }

  public void setValue(boolean value) {
    setValue(Boolean.toString(value));
  }

  public void setValue(int value) {
    setValue(Integer.toString(value));
  }

  public void setValue(String value) {
    resetCache();

    for (RegistryValueListener each : myListeners) {
      each.beforeValueChanged(this);
    }

    myRegistry.getUserProperties().put(myKey, value);

    for (RegistryValueListener each : myListeners) {
      each.afterValueChanged(this);
    }

    if (!isChangedFromDefault() && !isRestartRequired()) {
      myRegistry.getUserProperties().remove(myKey);
    }

    myChangedSinceStart = true;
    LOG.info("Registry value '" + myKey + "' has changed to '" + value + '\'');
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

  boolean isChangedSinceAppStart() {
    return myChangedSinceStart;
  }

  public void resetToDefault() {
    setValue(Registry.getInstance().getBundleValue(myKey, true));
  }

  public void addListener(@NotNull final RegistryValueListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, () -> myListeners.remove(listener));
  }

  @Override
  public String toString() {
    return myKey + "=" + asString();
  }

  void resetCache() {
    myStringCachedValue = null;
    myIntCachedValue = null;
    myDoubleCachedValue = Double.NaN;
    myBooleanCachedValue = null;
  }

  public boolean isBoolean() {
    return isBoolean(asString());
  }
  private static boolean isBoolean(String s) {
    return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
  }
}
