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

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Expose system-wide screen reader status for accessibility features.
 */
public class ScreenReader {
  public static final String SYSTEM_PROPERTY_KEY = "screenreader";
  public static final String ATK_WRAPPER = "org.GNOME.Accessibility.AtkWrapper";
  public static final String ACCESS_BRIDGE = "com.sun.java.accessibility.AccessBridge";

  /**
   * Components that need to customize their behavior in the presence of a external screen reader
   * application should call this method  to determine the presence of such screen reader.
   *
   * For example, this can be used to determine if components should to be focusable via keyboard
   * as opposed to accessible only via the mouse pointer.
   *
   * @return <code>true</code> if a screen reader is currently active, or has been active
   * since the start of the application.
   */
  public static boolean isActive() {
    // Return system property value if it is set
    if (SystemProperties.has(SYSTEM_PROPERTY_KEY)) {
      return SystemProperties.is(SYSTEM_PROPERTY_KEY);
    }

    // Auto-detect if system property not set.
    for (Frame frame: Frame.getFrames()) {
      if (frame instanceof AccessibleContextAccessor) {
        AccessibleContext frameContext = ((AccessibleContextAccessor)frame).getCurrentAccessibleContext();
        if (frameContext != null)
          return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a particular a11y technology is enabled, in order of priority:
   * 1) via the sys. property: "javax.accessibility.assistive_technologies"
   * 2) in the prop. file: <user home>/.accessibility.properties
   * 3) in the prop. file: <jre>/lib/accessibility.properties
   *
   * @see {@link #ACCESS_BRIDGE}
   * @see {@link #ATK_WRAPPER}
   * @param a11yClassName the full class name representing the a11y technology
   * @return true if enabled, otherwise false
   */
  public static boolean isEnabled(String a11yClassName) {
    String[] paths = new String[] {System.getProperty("user.home") + File.separator + ".accessibility.properties",
      System.getProperty("java.home") + File.separator + "lib" + File.separator + "accessibility.properties"};
    Properties properties = new Properties();
    for (String path : paths) {
      try {
        FileInputStream in = new FileInputStream(new File(path));
        try {
          properties.load(in);
        } finally {
          if (in != null) in.close();
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
}
