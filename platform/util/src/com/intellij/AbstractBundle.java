// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base class for particular scoped bundles (e.g. {@code 'vcs'} bundles, {@code 'aop'} bundles etc).
 * <p/>
 * Usage pattern:
 * <pre>
 * <ol>
 *   <li>Create class that extends this class and provides path to the target bundle to the current class constructor;</li>
 *   <li>
 *     Optionally create static facade method at the subclass - create single shared instance and delegate
 *     to its {@link #getMessage(String, Object...)};
 *   </li>
 * </ol>
 * </pre>
 *
 * @author Denis Zhdanov
 */
public abstract class AbstractBundle {
  private static final Logger LOG = Logger.getInstance(AbstractBundle.class);
  private Reference<ResourceBundle> myBundle;
  @NonNls private final String myPathToBundle;

  protected AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  @NotNull
  public String getMessage(@NotNull String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private ResourceBundle getBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(myBundle);
    if (bundle == null) {
      bundle = getResourceBundle(myPathToBundle, getClass().getClassLoader());
      myBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }

  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourCache =
    ConcurrentFactoryMap.createWeakMap(k -> ContainerUtil.createConcurrentSoftValueMap());

  public static ResourceBundle getResourceBundle(@NotNull String pathToBundle, @NotNull ClassLoader loader) {
    Map<String, ResourceBundle> map = ourCache.get(loader);
    ResourceBundle result = map.get(pathToBundle);
    if (result == null) {
      try {
        ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        result = ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader, control);
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
}
