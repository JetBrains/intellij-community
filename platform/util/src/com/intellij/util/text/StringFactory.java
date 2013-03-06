/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

public class StringFactory {
  private static final Constructor<String> sharingCtr;    // String(char[], boolean). Works since JDK1.7, earlier jdks have too slow reflection anyway

  static {
    Constructor<String> newC = null;
    try {
      newC = String.class.getDeclaredConstructor(char[].class, boolean.class);
      newC.setAccessible(true);
    }
    catch (NoSuchMethodException ignored) {
    }
    sharingCtr = newC;
  }

  /**
   * @return new instance of String which backed by chars array.
   *
   * CAUTION. EXTREMELY DANGEROUS.
   * DO NOT USE THIS METHOD UNLESS YOU ARE TOO DESPERATE
   */
  @NotNull
  public static String createShared(@NotNull char[] chars) {
    if (sharingCtr != null) {
      try {
        return sharingCtr.newInstance(chars, true);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return new String(chars);
  }
}
