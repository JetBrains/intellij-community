// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import com.intellij.util.ConcurrencyUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides a UI to configure internal settings of the IDE. Plugins can provide their own registry keys using the
 * {@code <registryKey>} extension point (see com.intellij.openapi.util.registry.RegistryKeyBean for more details).
 */
public class Registry  {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls
  public static final String REGISTRY_BUNDLE = "misc.registry";

  private final Map<String, String> myUserProperties = new LinkedHashMap<String, String>();
  private final ConcurrentMap<String, RegistryValue> myValues = new ConcurrentHashMap<String, RegistryValue>();
  private final Map<String, RegistryKeyDescriptor> myContributedKeys = new HashMap<String, RegistryKeyDescriptor>();

  private static final Registry ourInstance = new Registry();

  @NotNull
  public static RegistryValue get(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key) {
    final Registry registry = getInstance();

    RegistryValue value = registry.myValues.get(key);
    if (value == null) {
      value = ConcurrencyUtil.cacheOrGet(registry.myValues, key, new RegistryValue(registry, key, registry.myContributedKeys.get(key)));
    }
    return value;
  }

  public static boolean is(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key) throws MissingResourceException {
    return get(key).asBoolean();
  }

  public static boolean is(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key, boolean defaultValue) {
    try {
      return get(key).asBoolean();
    }
    catch (MissingResourceException ex) {
      return defaultValue;
    }
  }

  public static int intValue(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key) throws MissingResourceException {
    return get(key).asInteger();
  }

  public static int intValue(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key, int defaultValue) {
    try {
      return get(key).asInteger();
    }
    catch (MissingResourceException ex) {
      return defaultValue;
    }
  }

  public static double doubleValue(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key) throws MissingResourceException {
    return get(key).asDouble();
  }

  @NotNull
  public static String stringValue(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key) throws MissingResourceException {
    return get(key).asString();
  }

  public static Color getColor(@PropertyKey(resourceBundle = REGISTRY_BUNDLE) @NotNull String key, Color defaultValue) throws MissingResourceException {
    return get(key).asColor(defaultValue);
  }

  @NotNull
  static ResourceBundle getBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(REGISTRY_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }


  public String getBundleValue(@NotNull String key, boolean mustExist) throws MissingResourceException {
    if (myContributedKeys.containsKey(key)) {
      return myContributedKeys.get(key).getDefaultValue();
    }

    try {
      return getBundle().getString(key);
    }
    catch (MissingResourceException e) {
      if (mustExist) {
        throw e;
      }
    }

    return null;
  }

  @NotNull
  public static Registry getInstance() {
    return ourInstance;
  }

  @NotNull
  public Element getState() {
    final Element state = new Element("registry");
    for (String eachKey : myUserProperties.keySet()) {
      final Element entry = new Element("entry");
      entry.setAttribute("key", eachKey);
      entry.setAttribute("value", myUserProperties.get(eachKey));
      state.addContent(entry);
    }
    return state;
  }

  public void loadState(@NotNull Element state) {
    myUserProperties.clear();
    for (Element eachEntry : state.getChildren("entry")) {
      String key = eachEntry.getAttributeValue("key");
      String value = eachEntry.getAttributeValue("value");
      if (key != null && value != null) {
        RegistryValue registryValue = get(key);
        if (registryValue.isChangedFromDefault(value)) {
          myUserProperties.put(key, value);
          registryValue.resetCache();
        }
      }
    }
  }

  @NotNull
  Map<String, String> getUserProperties() {
    return myUserProperties;
  }

  @NotNull
  public static List<RegistryValue> getAll() {
    final ResourceBundle bundle = getBundle();
    final Enumeration<String> keys = bundle.getKeys();

    List<RegistryValue> result = new ArrayList<RegistryValue>();

    Map<String, RegistryKeyDescriptor> contributedKeys = getInstance().myContributedKeys;
    while (keys.hasMoreElements()) {
      final String each = keys.nextElement();
      if (each.endsWith(".description") || each.endsWith(".restartRequired") || contributedKeys.containsKey(each)) continue;
      result.add(get(each));
    }

    for (String key : contributedKeys.keySet()) {
      result.add(get(key));
    }

    return result;
  }

  void restoreDefaults() {
    Map<String, String> old = new HashMap<String, String>(myUserProperties);
    for (String each : old.keySet()) {
      try {
        get(each).resetToDefault();
      }
      catch (MissingResourceException e) {
        // outdated property that is not declared in registry.properties anymore
        myValues.remove(each);
      }
    }
  }

  boolean isInDefaultState() {
    return myUserProperties.isEmpty();
  }

  boolean isRestartNeeded() {
    return isRestartNeeded(myUserProperties);
  }

  private static boolean isRestartNeeded(@NotNull Map<String, String> map) {
    for (String s : map.keySet()) {
      final RegistryValue eachValue = get(s);
      if (eachValue.isRestartRequired() && eachValue.isChangedSinceAppStart()) return true;
    }

    return false;
  }

  public static void addKey(@NotNull String key, @NotNull String description, @NotNull String defaultValue, boolean restartRequired) {
    getInstance().myContributedKeys.put(key, new RegistryKeyDescriptor(key, description, defaultValue, restartRequired));
  }

  public static void addKey(@NotNull String key, @NotNull String description, int defaultValue, boolean restartRequired) {
    addKey(key, description, Integer.toString(defaultValue), restartRequired);
  }

  public static void addKey(@NotNull String key, @NotNull String description, boolean defaultValue, boolean restartRequired) {
    addKey(key, description, Boolean.toString(defaultValue), restartRequired);
  }
}
