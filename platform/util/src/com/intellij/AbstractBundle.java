// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Base class for particular scoped bundles (e.g. {@code 'vcs'} bundles, {@code 'aop'} bundles etc).
 * <p/>
 * Usage pattern:
 * <ol>
 *   <li>Create class that extends this class and provides path to the target bundle to the current class constructor;</li>
 *   <li>
 *     Optionally create static facade method at the subclass - create single shared instance and delegate
 *     to its {@link #getMessage(String, Object...)};
 *   </li>
 * </ol>
 *
 * @author Denis Zhdanov
 */
public abstract class AbstractBundle {
  private static final Logger LOG = Logger.getInstance(AbstractBundle.class);
  private Reference<ResourceBundle> myBundle;
  private Reference<ResourceBundle> myDefaultBundle;
  @NonNls private final String myPathToBundle;

  protected AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  @Contract(pure = true)
  public @NotNull @Nls String getMessage(@NotNull @NonNls String key, Object @NotNull ... params) {
    return message(getResourceBundle(), key, params);
  }

  /**
   * Performs partial application of the pattern message from the bundle leaving some parameters unassigned.
   * It's expected that the message contains params.length+unassignedParams placeholders. Parameters
   * {@code {0}..{params.length-1}} will be substituted using passed params array. The remaining parameters
   * will be renumbered: {@code {params.length}} will become {@code {0}} and so on, so the resulting template
   * could be applied once more.
   *
   * @param key resource key
   * @param unassignedParams number of unassigned parameters
   * @param params assigned parameters
   * @return a template suitable to pass to {@link MessageFormat#format(Object)} having the specified number of placeholders left
   */
  @Contract(pure = true)
  public @NotNull @Nls String getPartialMessage(@NotNull @NonNls String key, int unassignedParams, Object @NotNull ... params) {
    return BundleBase.partialMessage(getResourceBundle(), key, unassignedParams, params);
  }

  public @NotNull Supplier<@Nls String> getLazyMessage(@NotNull @NonNls String key, Object @NotNull ... params) {
    return () -> getMessage(key, params);
  }

  public @Nullable @Nls String messageOfNull(@NotNull @NonNls String key, Object @NotNull ... params) {
    return messageOrNull(getResourceBundle(), key, params);
  }

  public @Nls String messageOrDefault(@NotNull @NonNls String key,
                                      @Nullable @Nls String defaultValue,
                                      Object @NotNull ... params) {
    return messageOrDefault(getResourceBundle(), key, defaultValue, params);
  }

  @Contract("null, _, _, _ -> param3")
  public static @Nls String messageOrDefault(@Nullable ResourceBundle bundle,
                                             @NotNull @NonNls String key,
                                             @Nullable @Nls String defaultValue,
                                             Object @NotNull ... params) {
    if (bundle == null) {
      return defaultValue;
    }
    else if (!bundle.containsKey(key)) {
      return BundleBase.postprocessValue(bundle, BundleBase.useDefaultValue(bundle, key, defaultValue), params);
    }
    return BundleBase.messageOrDefault(bundle, key, defaultValue, params);
  }

  public static @Nls @NotNull String message(@NotNull ResourceBundle bundle, @NotNull @NonNls String key, Object @NotNull ... params) {
    return BundleBase.message(bundle, key, params);
  }

  public static @Nullable @Nls String messageOrNull(@NotNull ResourceBundle bundle, @NotNull @NonNls String key, Object @NotNull ... params) {
    @SuppressWarnings("HardCodedStringLiteral")
    String value = messageOrDefault(bundle, key, key, params);
    if (key.equals(value)) return null;
    return value;
  }

  public boolean containsKey(@NotNull @NonNls String key) {
    return getResourceBundle().containsKey(key);
  }

  public ResourceBundle getResourceBundle() {
    return getResourceBundle(null);
  }

  @ApiStatus.Internal
  protected @NotNull ResourceBundle getResourceBundle(@Nullable ClassLoader classLoader) {
    ResourceBundle bundle;
    if (DefaultBundleService.isDefaultBundle()) {
      bundle = getBundle(classLoader, myDefaultBundle);
      myDefaultBundle = new SoftReference<>(bundle);
    } else {
      bundle = getBundle(classLoader, myBundle);
      myBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }

  private ResourceBundle getBundle(@Nullable ClassLoader classLoader, @Nullable Reference<ResourceBundle> bundleReference) {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(bundleReference);
    if (bundle != null) {
      return bundle;
    }
    return getResourceBundle(myPathToBundle, classLoader == null ? getClass().getClassLoader() : classLoader);
  }

  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourCache =
    ConcurrentFactoryMap.createWeakMap(k -> ContainerUtil.createConcurrentSoftValueMap());

  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourDefaultCache =
    ConcurrentFactoryMap.createWeakMap(k -> ContainerUtil.createConcurrentSoftValueMap());

  public @NotNull ResourceBundle getResourceBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader) {
    return DefaultBundleService.isDefaultBundle()
           ? getResourceBundle(pathToBundle, loader, ourDefaultCache.get(loader))
           : getResourceBundle(pathToBundle, loader, ourCache.get(loader));
  }

  public ResourceBundle getResourceBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader, Map<String, ResourceBundle> map) {
    ResourceBundle result = map.get(pathToBundle);
    if (result == null) {
      try {
        ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        result = findBundle(pathToBundle, loader, control);
      }
      catch (MissingResourceException e) {
        LOG.info("Cannot load resource bundle from *.properties file, falling back to slow class loading: " + pathToBundle);
        ResourceBundle.clearCache(loader);
        result = ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader);
      }
      map.put(pathToBundle, result);
    }
    return result;
  }

  protected ResourceBundle findBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader, @NotNull ResourceBundle.Control control) {
    return ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader, control);
  }
}
