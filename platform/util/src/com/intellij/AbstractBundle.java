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
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ConcurrentSoftValueHashMap;
import com.intellij.util.containers.ConcurrentWeakFactoryMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base class for particular scoped bundles (e.g. <code>'vcs'</code> bundles, <code>'aop'</code> bundles etc).
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
 * @since 8/1/11 2:37 PM
 */
public abstract class AbstractBundle {
  private static final Logger LOG = Logger.getInstance("#com.intellij.AbstractBundle");
  private Reference<ResourceBundle> myBundle;
  @NonNls private final String myPathToBundle;

  protected AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  public String getMessage(@NotNull String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (myBundle != null) bundle = myBundle.get();
    if (bundle == null) {
      bundle = getResourceBundle(myPathToBundle, getClass().getClassLoader());
      myBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final FactoryMap<ClassLoader, Map<String, ResourceBundle>> ourCache =
    new ConcurrentWeakFactoryMap<ClassLoader, Map<String, ResourceBundle>>() {
      @Override
      protected Map<String, ResourceBundle> create(ClassLoader key) {
        return new ConcurrentSoftValueHashMap<String, ResourceBundle>();
      }
    };

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
