// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorHexUtil;
import com.intellij.util.containers.ContainerUtil;
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
  private Double myDoubleCachedValue;
  private Boolean myBooleanCachedValue;
  private static final Logger LOG = Logger.getInstance(RegistryValue.class);

  RegistryValue(@NotNull Registry registry, @NotNull String key, @Nullable RegistryKeyDescriptor keyDescriptor) {
    myRegistry = registry;
    myKey = key;
    myKeyDescriptor = keyDescriptor;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }


  @NotNull
  public String asString() {
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
      myIntCachedValue = result = calcInt();
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

  public double asDouble() {
    Double result = myDoubleCachedValue;
    if (result == null) {
      myDoubleCachedValue = result = calcDouble();
    }
    return result.doubleValue();
  }

  @NotNull
  private Double calcDouble() {
    try {
      return Double.valueOf(get(myKey, "0.0", true));
    }
    catch (NumberFormatException e) {
      String bundleValue = Registry.getInstance().getBundleValue(myKey, true);
      assert bundleValue != null;
      return Double.valueOf(bundleValue);
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
  public String getDescription() {
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
    return isChangedFromDefault(asString());
  }

  public boolean isContributedByThirdPartyPlugin() {
    return myKeyDescriptor != null && myKeyDescriptor.isContributedByThirdPartyPlugin();
  }

  boolean isChangedFromDefault(@NotNull String newValue) {
    return !newValue.equals(Registry.getInstance().getBundleValue(myKey, false));
  }

  protected String get(@NotNull String key, String defaultValue, boolean isValue) throws MissingResourceException {
    if (isValue) {
      if (myStringCachedValue == null) {
        myStringCachedValue = _get(key, defaultValue, true);
      }
      return myStringCachedValue;
    }
    return _get(key, defaultValue, false);
  }

  private String _get(@NotNull String key, String defaultValue, boolean mustExistInBundle) throws MissingResourceException {
    final String userValue = myRegistry.getUserProperties().get(key);
    if (userValue != null) {
      return userValue;
    }
    String systemProperty = System.getProperty(key);
    if (systemProperty != null) {
      return systemProperty;
    }
    final String bundleValue = Registry.getInstance().getBundleValue(key, mustExistInBundle);
    if (bundleValue != null) {
      return bundleValue;
    }
    return defaultValue;
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
    myDoubleCachedValue = null;
    myBooleanCachedValue = null;
  }

  public boolean isBoolean() {
    return isBoolean(asString());
  }
  private static boolean isBoolean(String s) {
    return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
  }
}
