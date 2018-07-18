/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

@ReviseWhenPortedToJDK("9")
public class StringFactory {
  // String(char[], boolean). Works since JDK1.7, earlier JDKs have too slow reflection anyway
  private static final Constructor<String> ourConstructor;

  static {
    Constructor<String> constructor = null;
    // makes no sense in JDK9 because new String(char[],boolean) there parses and copies array too.
    if (!SystemInfo.IS_AT_LEAST_JAVA9) {
      try {
        constructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
        constructor.setAccessible(true);
      }
      catch (Throwable ignored) {
        constructor = null; // setAccessible fails without explicit permission on Java 9
      }
    }
    ourConstructor = constructor;
  }

  /**
   * @return new instance of String which backed by given char array.
   *
   * CAUTION! EXTREMELY DANGEROUS!! DO NOT USE THIS METHOD UNLESS YOU ARE REALLY DESPERATE!!!
   */
  @NotNull
  public static String createShared(@NotNull char[] chars) {
    if (ourConstructor != null) {
      try {
        return ourConstructor.newInstance(chars, Boolean.TRUE);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    return new String(chars);
  }
}