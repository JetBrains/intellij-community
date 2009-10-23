/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util;

/**
 * @author Konstantin Bulenkov
 */
public final class NullUtils {
  /**
   * Returns <tt>true</tt> if and only if all objects are not <tt>null</tt>
   *
   * @param objects objects to check
   * @return <tt>true</tt> if all objects are not <tt>null</tt>,
   *         otherwise <tt>false</tt>
   */
  public static boolean notNull(Object... objects) {
    for (Object object : objects) {
      if (object == null) return false;
    }
    return true;
  }

  /**
   * Returns <tt>true</tt> if and only if at least one object is <tt>null</tt>
   *
   * @param objects objects to check
   * @return <tt>false</tt> if all objects are not <tt>null</tt>,
   *         otherwise <tt>true</tt>
   */
  public static boolean hasNull(Object... objects) {
    for (Object object : objects) {
      if (object == null) return true;
    }
    return false;
  }

  /**
   * Returns <tt>true</tt> if and only if at least one object is not <tt>null</tt>
   *
   * @param objects objects to check
   * @return <tt>false</tt> if all objects are <tt>null</tt>,
   *         otherwise <tt>true</tt>
   */
  public static boolean hasNotNull(Object... objects) {
    for (Object object : objects) {
      if (object != null) return true;
    }
    return false;
  }

  private NullUtils() {}
}
