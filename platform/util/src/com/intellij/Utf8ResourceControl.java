// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

final class Utf8ResourceControl extends ResourceBundle.Control {
  public static final Utf8ResourceControl INSTANCE = new Utf8ResourceControl();

  @Override
  //copy-paste from java8 control with encoding fixes
  public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                  ClassLoader loader, boolean reload)
    throws IllegalAccessException, InstantiationException, IOException {
    String bundleName = toBundleName(baseName, locale);
    ResourceBundle bundle = null;
    if (format.equals("java.class")) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends ResourceBundle> bundleClass
          = (Class<? extends ResourceBundle>)loader.loadClass(bundleName);

        // If the class isn't a ResourceBundle subclass, throw a
        // ClassCastException.
        if (ResourceBundle.class.isAssignableFrom(bundleClass)) {
          bundle = bundleClass.newInstance();
        }
        else {
          throw new ClassCastException(bundleClass.getName()
                                       + " cannot be cast to ResourceBundle");
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
    else if (format.equals("java.properties")) {
      if (bundleName.contains("://")) {
        return bundle;
      }
      final String resourceName = toResourceName(bundleName, "properties");

      final ClassLoader classLoader = loader;
      final boolean reloadFlag = reload;
      InputStream stream = null;
      try {
        stream = AccessController.doPrivileged(
          new PrivilegedExceptionAction<InputStream>() {
            @Override
            public InputStream run() throws IOException {
              InputStream is = null;
              if (reloadFlag) {
                URL url = classLoader.getResource(resourceName);
                if (url != null) {
                  URLConnection connection = url.openConnection();
                  if (connection != null) {
                    // Disable caches to get fresh data for
                    // reloading.
                    connection.setUseCaches(false);
                    is = connection.getInputStream();
                  }
                }
              }
              else {
                is = classLoader.getResourceAsStream(resourceName);
              }
              return is;
            }
          });
      }
      catch (PrivilegedActionException e) {
        throw (IOException)e.getException();
      }
      if (stream != null) {
        try {
          bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        finally {
          stream.close();
        }
      }
    }
    else {
      throw new IllegalArgumentException("unknown format: " + format);
    }
    return bundle;
  }

  @Override
  public List<String> getFormats(String baseName) {
    if (baseName == null) {
      throw new NullPointerException();
    }
    return FORMAT_PROPERTIES;
  }
}
