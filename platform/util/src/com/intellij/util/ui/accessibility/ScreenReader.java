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

/**
 * Expose system-wide screen reader status for accessibility features.
 */
public class ScreenReader {
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
    myActive = active;
  }
}
