/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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

  private final List<RegistryValueListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myChangedSinceStart;

  private String myStringCachedValue;
  private Integer myIntCachedValue;
  private Double myDoubleCachedValue;
  private Boolean myBooleanCachedValue;

  RegistryValue(Registry registry, String key) {
    myRegistry = registry;
    myKey = key;
  }

  public String getKey() {
    return myKey;
  }


  public String asString() {
    final String value = get(myKey, null, true);
    assert value != null : myKey;
    return value;
  }

  public boolean asBoolean() {
    if (myBooleanCachedValue == null) {
      myBooleanCachedValue = Boolean.valueOf(get(myKey, "false", true));
    }

    return myBooleanCachedValue.booleanValue();
  }

  public int asInteger() {
    if (myIntCachedValue == null) {
      myIntCachedValue = Integer.valueOf(get(myKey, "0", true));
    }

    return myIntCachedValue.intValue();
  }

  public double asDouble() {
    if (myDoubleCachedValue == null) {
      myDoubleCachedValue = Double.valueOf(get(myKey, "0.0", true));
    }

    return myDoubleCachedValue.doubleValue();
  }

  public Color asColor(Color defaultValue) {
    final String s = get(myKey, null, true);
    if (s != null) {
      final String[] rgb = s.split(",");
      if (rgb.length == 3) {
        try {
          return new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
        }
        catch (Exception e) {//
        }
      }
    }
    return defaultValue;
  }

  public String getDescription() {
    return get(myKey + ".description", "", false);
  }

  public boolean isRestartRequired() {
    return Boolean.valueOf(get(myKey + ".restartRequired", "false", false));
  }

  public boolean isChangedFromDefault() {
    return !asString().equals(getBundleValue(myKey, false));
  }

  private String get(String key, String defaultValue, boolean isValue) {
    if (isValue) {
      if (myStringCachedValue == null) {
        myStringCachedValue = _get(key, defaultValue, isValue);
      }
      if (isBoolean()) {
        myStringCachedValue = Boolean.valueOf(myStringCachedValue).toString();
      }
      return myStringCachedValue;
    }
    else {
      return _get(key, defaultValue, isValue);
    }
  }

  private String _get(String key, String defaultValue, boolean mustExistInBundle) {
    final String userValue = myRegistry.getUserProperties().get(key);
    if (userValue == null) {
      String systemProperty = System.getProperty(key);
      if (systemProperty != null) {
        return systemProperty;
      }
      final String bundleValue = getBundleValue(key, mustExistInBundle);
      if (bundleValue != null) {
        return bundleValue;
      }
      else {
        return defaultValue;
      }
    }
    else {
      return userValue;
    }
  }

  private String getBundleValue(String key, boolean mustExist) {
    try {
      return Registry.getBundle().getString(key);
    }
    catch (MissingResourceException e) {
      if (mustExist) {
        throw e;
      }
    }

    return null;
  }

  public void setValue(boolean value) {
    setValue(Boolean.valueOf(value).toString());
  }

  public void setValue(int value) {
    setValue(Integer.valueOf(value).toString());
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

    if (!isChangedFromDefault()) {
      myRegistry.getUserProperties().remove(myKey);
    }

    myChangedSinceStart = true;
  }

  public boolean isChangedSinceAppStart() {
    return myChangedSinceStart;
  }

  public void resetToDefault() {
    setValue(getBundleValue(myKey, true));
  }

  public void addListener(@NotNull final RegistryValueListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  @Override
  public String toString() {
    return myKey + "=" + asString();
  }

  void resetCache() {
    myStringCachedValue = null;
    myIntCachedValue = null;
    myBooleanCachedValue = null;
  }

  public boolean isBoolean() {
    return "true".equals(myStringCachedValue) || "false".equals(myStringCachedValue);
  }
}
