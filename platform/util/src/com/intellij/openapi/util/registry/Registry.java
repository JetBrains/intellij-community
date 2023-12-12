// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.MathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Provides a UI to configure internal settings of the IDE.
 * <p>
 * Plugins can provide their own registry keys using the
 * {@code com.intellij.registryKey} extension point (see {@link RegistryKeyBean} for more details).
 */
public final class Registry {
  // TODO: Extract the common property-related logic into a new Kotlin class
  private static Reference<Map<String, String>> bundledRegistryOverrides;
  private static Reference<Map<String, String>> bundledRegistry;

  public static final @NonNls String REGISTRY_BUNDLE = "misc.registry";

  private static final RegistryValueListener EMPTY_VALUE_LISTENER = new RegistryValueListener() {
  };

  private final Map<String, String> myUserProperties = new LinkedHashMap<>();
  private final Map<String, RegistryValue> myValues = new ConcurrentHashMap<>();
  private Map<String, RegistryKeyDescriptor> myContributedKeys = Collections.emptyMap();

  private static final Registry ourInstance = new Registry();
  private volatile boolean isLoaded;

  private volatile @NotNull RegistryValueListener valueChangeListener = EMPTY_VALUE_LISTENER;

  public static @NotNull RegistryValue get(@NonNls @NotNull String key) {
    return getInstance().doGet(key);
  }

  @ApiStatus.Internal
  public static @NotNull RegistryValue _getWithoutStateCheck(@NonNls @NotNull String key) {
    return ourInstance.doGet(key);
  }

  private @NotNull RegistryValue doGet(@NonNls @NotNull String key) {
    return myValues.computeIfAbsent(key, s -> new RegistryValue(this, s, myContributedKeys.get(s)));
  }

  public static boolean is(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asBoolean();
  }

  public static boolean is(@NonNls @NotNull String key, boolean defaultValue) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return defaultValue;
    }

    try {
      return getInstance().doGet(key).asBoolean();
    }
    catch (MissingResourceException ignore) {
      return defaultValue;
    }
  }

  public static int intValue(@NonNls @NotNull String key) throws MissingResourceException {
    return getInstance().doGet(key).asInteger();
  }

  public static int intValue(@NonNls @NotNull String key, int defaultValue) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      LoadingState.COMPONENTS_REGISTERED.checkOccurred();
      return defaultValue;
    }

    try {
      return getInstance().doGet(key).asInteger();
    }
    catch (MissingResourceException ignore) {
      return defaultValue;
    }
  }

  @TestOnly
  void reset() {
    myUserProperties.clear();
    myValues.clear();
    isLoaded = false;
  }

  public static double doubleValue(@NonNls @NotNull String key, double defaultValue) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      LoadingState.COMPONENTS_REGISTERED.checkOccurred();
      return defaultValue;
    }

    try {
      return getInstance().doGet(key).asDouble();
    }
    catch (MissingResourceException ignore) {
      return defaultValue;
    }
  }

  public static double doubleValue(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asDouble();
  }

  public static @NotNull String stringValue(@NonNls @NotNull String key) throws MissingResourceException {
    return get(key).asString();
  }

  public static Color getColor(@NonNls @NotNull String key, Color defaultValue) throws MissingResourceException {
    return get(key).asColor(defaultValue);
  }

  private static Map<String, String> loadOverridesFromBundledConfig() throws IOException {
    Reference<Map<String, String>> bundleRef = bundledRegistryOverrides;
    Map<String, String> result = bundleRef == null ? null : bundleRef.get();
    if (result != null) {
      return result;
    }

    result = loadFromBundledConfig("misc/registry.override.properties", 32);

    bundledRegistryOverrides = new SoftReference<>(result);
    return result;
  }

  private static Map<String, String> loadFromBundledConfig() throws IOException {
    Reference<Map<String, String>> bundleRef = bundledRegistry;
    Map<String, String> result = bundleRef == null ? null : bundleRef.get();
    if (result != null) {
      return result;
    }

    result = loadFromBundledConfig("misc/registry.properties", 1800);

    bundledRegistry = new SoftReference<>(result);
    return result;
  }

  private static Map<String, String> loadFromBundledConfig(String resourceName, int initialCapacity) throws IOException {
    Map<String, String> map = new LinkedHashMap<>(initialCapacity);
    if (loadFromResource(resourceName, map)) {
      return map;
    }

    return Collections.emptyMap();
  }

  private static boolean loadFromResource(String sourceResourceName, Map<String, String> targetMap) throws IOException {
    InputStream stream = Registry.class.getClassLoader().getResourceAsStream(sourceResourceName);
    if (stream == null) {
      return false;
    }

    try {
      //noinspection NonSynchronizedMethodOverridesSynchronizedMethod
      new Properties() {
        @Override
        public Object put(Object key, Object value) {
          return targetMap.put((String)key, (String)value);
        }
      }.load(stream);
    }
    finally {
      stream.close();
    }
    return true;
  }

  public @NlsSafe @Nullable String getBundleValueOrNull(@NonNls @NotNull String key) {
    try {
      Map<String, String> bundle = loadOverridesFromBundledConfig();
      String overriddenValue = bundle.get(key);
      if (overriddenValue != null) {
        return overriddenValue;
      }
    }
    catch (IOException e) {
      // critical start-up error (cannot parse properties file), don't bother clients
      throw new UncheckedIOException(e);
    }

    RegistryKeyDescriptor contributed = myContributedKeys.get(key);
    if (contributed != null) {
      return contributed.getDefaultValue();
    }

    try {
      Map<String, String> bundle = loadFromBundledConfig();
      return bundle.get(key);
    }
    catch (IOException e) {
      // critical start-up error (cannot parse properties file), don't bother clients
      throw new UncheckedIOException(e);
    }
  }

  @NlsSafe @NotNull String getBundleValue(@NonNls @NotNull String key) throws MissingResourceException {
    String result = getBundleValueOrNull(key);
    if (result == null) {
      throw new MissingResourceException("Registry key " + key + " is not defined", REGISTRY_BUNDLE, key);
    }
    return result;
  }

  public static @NotNull Registry getInstance() {
    LoadingState.COMPONENTS_LOADED.checkOccurred();
    return ourInstance;
  }

  public @NotNull Element getState() {
    Element state = new Element("registry");
    for (Map.Entry<String, String> entry : myUserProperties.entrySet()) {
      RegistryValue registryValue = get(entry.getKey());
      if (registryValue.isChangedFromDefault()) {
        Element entryElement = new Element("entry");
        entryElement.setAttribute("key", entry.getKey());
        entryElement.setAttribute("value", entry.getValue());
        state.addContent(entryElement);
      }
    }
    return state;
  }

  public static int intValue(@NonNls @NotNull String key, int defaultValue, int minValue, int maxValue) {
    if (defaultValue < minValue || defaultValue > maxValue) {
      throw new IllegalArgumentException("Wrong values for default:min:max (" + defaultValue + ":" + minValue + ":" + maxValue + ")");
    }
    return MathUtil.clamp(intValue(key, defaultValue), minValue, maxValue);
  }

  private static @NotNull Map<String, String> fromState(@NotNull Element state) {
    Map<String, String> map = new LinkedHashMap<>();
    for (Element eachEntry : state.getChildren("entry")) {
      String key = eachEntry.getAttributeValue("key");
      String value = eachEntry.getAttributeValue("value");
      if (key != null && value != null) {
        map.put(key, value);
      }
    }
    return map;
  }

  private static @NotNull Map<String, String> updateStateInternal(@NotNull Registry registry, @Nullable Element state) {
    Map<String, String> userProperties = registry.myUserProperties;
    if (state == null) {
      userProperties.clear();
      return userProperties;
    }

    Map<String, String> map = fromState(state);
    Set<String> keys2process = new HashSet<>(userProperties.keySet());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      RegistryValue registryValue = registry.doGet(entry.getKey());
      String currentValue = registryValue.get(entry.getKey(), null, false);
      // currentValue == null means value is not in the bundle. Simply ignore it
      if (currentValue != null && !currentValue.equals(entry.getValue())) {
        registryValue.setValue(entry.getValue());
      }
      keys2process.remove(entry.getKey());
    }

    // keys that are not in the state, we need to reset them to default value
    for (String key : keys2process) {
      registry.doGet(key).resetToDefault();
    }

    return userProperties;
  }

  @ApiStatus.Internal
  public static @NotNull Map<String, String> loadState(@Nullable Element state, @Nullable Map<String, String> earlyAccess) {
    Registry registry = ourInstance;
    if (registry.isLoaded) {
      return updateStateInternal(registry, state);
    }
    else {
      return loadStateInternal(registry, state, earlyAccess);
    }
  }

  @ApiStatus.Internal
  public static void markAsLoaded() {
    ourInstance.isLoaded = true;
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  @NotNull Map<String, String> getUserProperties() {
    return myUserProperties;
  }

  @ApiStatus.Internal
  public static @NotNull List<RegistryValue> getAll() {
    Map<String, String> bundle = null;
    try {
      bundle = loadFromBundledConfig();
    }
    catch (IOException ignored) {
    }
    Set<String> keys = bundle == null ? Collections.emptySet() : bundle.keySet();

    try {
      bundle = loadOverridesFromBundledConfig();
      keys.addAll(bundle.keySet());
    }
    catch (IOException ignored) {
    }
    
    List<RegistryValue> result = new ArrayList<>();
    // don't use getInstance here - https://youtrack.jetbrains.com/issue/IDEA-271748
    Registry instance = ourInstance;
    Map<String, RegistryKeyDescriptor> contributedKeys = instance.myContributedKeys;
    for (String key : keys) {
      if (key.endsWith(".description") || key.endsWith(".restartRequired") || contributedKeys.containsKey(key)) {
        continue;
      }
      result.add(instance.doGet(key));
    }

    for (String key : contributedKeys.keySet()) {
      result.add(instance.doGet(key));
    }

    return result;
  }

  void restoreDefaults() {
    Map<String, String> old = new LinkedHashMap<>(myUserProperties);
    Registry instance = getInstance();
    for (String key : old.keySet()) {
      String v = instance.getBundleValueOrNull(key);
      if (v == null) {
        // outdated property that is not declared in registry.properties anymore
        myValues.remove(key);
      }
      else {
        RegistryValue value = instance.myValues.get(key);
        if (value != null) {
          value.setValue(v);
        }
      }
    }
  }

  boolean isInDefaultState() {
    return myUserProperties.isEmpty();
  }

  public boolean isRestartNeeded() {
    return isRestartNeeded(myUserProperties);
  }

  private static boolean isRestartNeeded(@NotNull Map<String, String> map) {
    Registry instance = getInstance();
    for (String s : map.keySet()) {
      RegistryValue eachValue = instance.doGet(s);
      if (eachValue.isRestartRequired() && eachValue.isChangedSinceAppStart()) {
        return true;
      }
    }

    return false;
  }

  @ApiStatus.Internal
  public static synchronized void setKeys(@NotNull Map<String, RegistryKeyDescriptor> descriptors) {
    // getInstance must be not used here - phase COMPONENT_REGISTERED is not yet completed
    ourInstance.myContributedKeys = descriptors;
  }

  @ApiStatus.Internal
  public static synchronized void mutateContributedKeys(@NotNull Function<? super Map<String, RegistryKeyDescriptor>, ? extends Map<String, RegistryKeyDescriptor>> mutator) {
    // getInstance must be not used here - phase COMPONENT_REGISTERED is not yet completed
    ourInstance.myContributedKeys = mutator.apply(ourInstance.myContributedKeys);
  }

  @ApiStatus.Internal
  public static void setValueChangeListener(@Nullable RegistryValueListener listener) {
    ourInstance.valueChangeListener = listener == null ? EMPTY_VALUE_LISTENER : listener;
  }

  @NotNull RegistryValueListener getValueChangeListener() {
    return valueChangeListener;
  }

  private static @NotNull Map<String, String> loadStateInternal(@NotNull Registry registry,
                                                                @Nullable Element state,
                                                                @Nullable Map<String, String> earlyAccess) {
    Map<String, String> userProperties = registry.myUserProperties;
    userProperties.clear();
    if (state != null) {
      Map<String, String> map = fromState(state);
      for (Map.Entry<String, String> entry : map.entrySet()) {
        RegistryValue registryValue = registry.doGet(entry.getKey());
        if (registryValue.isChangedFromDefault(entry.getValue(), registry)) {
          userProperties.put(entry.getKey(), entry.getValue());
          registryValue.resetCache();
        }
      }
    }

    if (earlyAccess != null) {
      // yes, earlyAccess overrides user properties
      userProperties.putAll(earlyAccess);
    }
    registry.isLoaded = true;
    return userProperties;
  }
}
