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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;

import javax.accessibility.AccessibleContext;
import java.awt.*;

/**
 * Expose system-wide screen reader status for accessibility features.
 */
public class ScreenReader {
  public static String SYSTEM_PROPERTY_KEY = "screenreader";
  /**
   * <p>Cache the <b>positive</b> state of having an active screen reader.</p>
   *
   * <p><b>Note:</b>
   * Screen readers always attach to applications after some delay, due to the
   * way Java Access Bridge events work. The delay varies between screen readers, as
   * it depends on how the initial handshake is implemented by the screen reader.</p>
   *
   * <p>This means caching the negative case would require heuristics to deal
   * with the non-determinism introduced by this delay. In other words, if we don't
   * detect an active screen reader at some point in time, how long can we remember
   * that fact?</p>
   *
   * <p>However, caching the positive case is safe, as we can assume that once we detect
   * an active screen reader, we can remember that fact until the application is
   * terminated -- the way {@link javax.accessibility.Accessible#getAccessibleContext}
   * is implemented follows the same assumption.</p>
   *
   * <p>Also, it turns out caching the positive case helps alleviate the latency
   * issue in the case where the application keeps closing and opening top level
   * windows, as each new window has a {@code null} {@link AccessibleContext} until
   * the screen reader attaches to the window. By remembering the "positive" state,
   * we ensure screen reader latency only affects the very first window opened by
   * the application.</p>
   */
  private static boolean myActiveCache = false;

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
    if (myActiveCache)
      return true;

    // Return system property value if it is set
    if (SystemProperties.has(SYSTEM_PROPERTY_KEY)) {
      return SystemProperties.is(SYSTEM_PROPERTY_KEY);
    }

    // Auto-detect if system property not set
    // Note: Only on Windows until Mac/Linux are fully supported.
    if (SystemInfo.isWindows) {
      for (Frame frame : Frame.getFrames()) {
        if (frame instanceof AccessibleContextAccessor) {
          AccessibleContext frameContext = ((AccessibleContextAccessor)frame).getCurrentAccessibleContext();
          if (frameContext != null)
            return true;
        }
      }
    }
    return false;
  }
}
