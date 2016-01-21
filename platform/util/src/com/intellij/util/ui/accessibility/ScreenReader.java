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

import javax.accessibility.AccessibleContext;
import java.awt.*;

/**
 * Expose system-wide screen reader status for accessibility features.
 */
public class ScreenReader {
  public static String SYSTEM_PROPERTY_KEY = "screenreader";

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
}
