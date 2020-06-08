/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.accessibility;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Expose system-wide screen reader status for accessibility features.
 */
public final class ScreenReader {
  public static final String ATK_WRAPPER = "org.GNOME.Accessibility.AtkWrapper";
  public static final String ACCESS_BRIDGE = "com.sun.java.accessibility.AccessBridge";

  private static final PropertyChangeSupport PCS = new PropertyChangeSupport(new ScreenReader());
  public static final String SCREEN_READER_ACTIVE_PROPERTY = "ScreenReader.active";

  private static boolean myActive = false;

  /**
   * Components that need to customize their behavior in the presence of an external screen reader
   * application should call this method. For example, this can be used to determine if components
   * should to be focusable via keyboard -- as opposed to accessible only via the mouse pointer.
   */
  public static boolean isActive() {
    return myActive;
  }

  /**
   * This method should be called by a central configuration mechanism to enable screen reader
   * support for the application.
   */
  public static void setActive(boolean active) {
    boolean oldValue = myActive;
    myActive = active;
    PCS.firePropertyChange(SCREEN_READER_ACTIVE_PROPERTY, oldValue, active);
  }

  /**
   * Checks whether a particular a11y technology is enabled, in order of priority:
   * 1) via the sys. property: "javax.accessibility.assistive_technologies"
   * 2) in the prop. file: <user home>/.accessibility.properties
   * 3) in the prop. file: <jre>/lib/accessibility.properties
   *
   * @see #ACCESS_BRIDGE
   * @see #ATK_WRAPPER
   * @param a11yClassName the full class name representing the a11y technology
   * @return true if enabled, otherwise false
   */
  public static boolean isEnabled(String a11yClassName) {
    String[] paths = new String[]{
      System.getProperty("user.home") + File.separator + ".accessibility.properties",
      System.getProperty("java.home") + File.separator + "lib" + File.separator + "accessibility.properties"
    };
    Properties properties = new Properties();
    for (String path : paths) {
      try {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
          properties.load(in);
        }
      }
      catch (Exception ignore) {
        continue;
      }
      if (!properties.isEmpty()) break;
    }
    if (!properties.isEmpty()) {
      // First, check the system property
      String classNames = System.getProperty("javax.accessibility.assistive_technologies");
      if (classNames == null) {
        // If the system property is not set, Toolkit will try to use the properties file.
        classNames = properties.getProperty("assistive_technologies", null);
      }
      if (classNames != null && classNames.contains(a11yClassName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds property change listener. Supported properties:
   * {@link #SCREEN_READER_ACTIVE_PROPERTY}
   */
  public static void addPropertyChangeListener(@NotNull final String propertyName,
                                               @NotNull Disposable parent,
                                               @NotNull final PropertyChangeListener listener) {
    PCS.addPropertyChangeListener(propertyName, listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        PCS.removePropertyChangeListener(propertyName, listener);
      }
    });
  }
}
