// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.ResourceBundle;

public final class BundleUtil {
  private final static Method SET_PARENT = ReflectionUtil.getDeclaredMethod(ResourceBundle.class, "setParent", ResourceBundle.class);
  private final static Logger LOG = Logger.getInstance(BundleUtil.class);

  @Nullable
  public static ResourceBundle loadLanguageBundle(@Nullable ClassLoader pluginClassLoader, String name) {
    if (pluginClassLoader == null) return null;
    ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    ResourceBundle pluginBundle = ResourceBundle.getBundle(name, Locale.getDefault(), pluginClassLoader, control);

    if (pluginBundle == null) return null;
    ResourceBundle base = ResourceBundle.getBundle(name);
    try {
      if (SET_PARENT != null) {
        SET_PARENT.invoke(pluginBundle, base);
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }

    return pluginBundle;
  }
}
