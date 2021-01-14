// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.lang.JavaVersion;
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
 * <br/>
 * <b>This class is not supposed to be extended directly. Extend your bundle from {@link com.intellij.DynamicBundle} or {@link org.jetbrains.jps.api.JpsDynamicBundle}</b>
 *
 * @author Denis Zhdanov
 */
public class AbstractBundle {
  private static final Logger LOG = Logger.getInstance(AbstractBundle.class);

  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourCache = CollectionFactory.createConcurrentWeakMap();
  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourDefaultCache = CollectionFactory.createConcurrentWeakMap();

  private Reference<ResourceBundle.Control> myControl;
  private Reference<ResourceBundle> myBundle;
  private Reference<ResourceBundle> myDefaultBundle;
  private final @NonNls String myPathToBundle;

  public AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  @Contract(pure = true)
  public @NotNull @Nls String getMessage(@NotNull @NonNls String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(getResourceBundle(getClass().getClassLoader()), key, null, params);
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

  public @Nullable @Nls String messageOrNull(@NotNull @NonNls String key, Object @NotNull ... params) {
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
    else {
      return BundleBase.messageOrDefault(bundle, key, defaultValue, params);
    }
  }

  public static @Nls @NotNull String message(@NotNull ResourceBundle bundle, @NotNull @NonNls String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(bundle, key, null, params);
  }

  public static @Nullable @Nls String messageOrNull(@NotNull ResourceBundle bundle, @NotNull @NonNls String key, Object @NotNull ... params) {
    @SuppressWarnings("HardCodedStringLiteral")
    String value = messageOrDefault(bundle, key, key, params);
    return key.equals(value) ? null : value;
  }

  public boolean containsKey(@NotNull @NonNls String key) {
    return getResourceBundle().containsKey(key);
  }

  public ResourceBundle getResourceBundle() {
    return getResourceBundle(getClass().getClassLoader());
  }

  @ApiStatus.Internal
  public final @NotNull ResourceBundle getResourceBundle(@NotNull ClassLoader classLoader) {
    boolean isDefault = DefaultBundleService.isDefaultBundle();
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(isDefault ? myDefaultBundle : myBundle);
    if (bundle == null) {
      bundle = resolveResourceBundle(myPathToBundle, classLoader);
      SoftReference<ResourceBundle> ref = new SoftReference<>(bundle);
      if (isDefault) {
        myDefaultBundle = ref;
      }
      else {
        myBundle = ref;
      }
    }
    return bundle;
  }

  public final @NotNull ResourceBundle getResourceBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader) {
    if (pathToBundle.equals(myPathToBundle)) {
      return getResourceBundle(loader);
    }

    Map<String, ResourceBundle> cache = (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(loader, __ -> CollectionFactory.createConcurrentSoftValueMap());
    ResourceBundle result = cache.get(pathToBundle);
    if (result == null) {
      result = resolveResourceBundle(pathToBundle, loader);
      cache.put(pathToBundle, result);
    }
    return result;
  }

  private @NotNull ResourceBundle resolveResourceBundle(@NotNull String pathToBundle, @NotNull ClassLoader loader) {
    try {
      ResourceBundle.Control control = getResourceBundleControl();
      return findBundle(pathToBundle, loader, control);
    }
    catch (MissingResourceException e) {
      LOG.info("Cannot load resource bundle from *.properties file, falling back to slow class loading: " + pathToBundle);
      ResourceBundle.clearCache(loader);
      return ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader);
    }
  }
  //we need return UTF-8 control for java <= 1.8
  //Before java9 ISO-8859-1 was used, in java 9 and above UTF-8
  //see https://docs.oracle.com/javase/9/docs/api/java/util/PropertyResourceBundle.html and
  //https://docs.oracle.com/javase/8/docs/api/java/util/PropertyResourceBundle.html
  //for more details
  @ReviseWhenPortedToJDK("9")
  private ResourceBundle.Control getResourceBundleControl() {
    ResourceBundle.Control control = com.intellij.reference.SoftReference.dereference(myControl);
    if (control == null) {
      if (JavaVersion.current().feature >= 9) {
        control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
      }
      else {
        control = Utf8ResourceControl.INSTANCE;
      }
      myControl = new com.intellij.reference.SoftReference<>(control);
    }
    return control;
  }

  protected ResourceBundle findBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader, @NotNull ResourceBundle.Control control) {
    return ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader, control);
  }

  protected static void clearGlobalLocaleCache() {
    ourCache.clear();
  }

  public void clearLocaleCache() {
    if (myBundle != null) {
      myBundle.clear();
    }
  }
}
