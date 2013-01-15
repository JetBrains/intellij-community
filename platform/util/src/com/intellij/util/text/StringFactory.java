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
  private static final Constructor<String> sharingCtr;   // String(char[], boolean)
  private static final Constructor<String> oldSharingCtr; // String(int offset, int count, char value[]) in ancient java 1.6

  static {
    Constructor<String> newC = null;
    Constructor<String> oldC = null;
    try {
      newC = String.class.getDeclaredConstructor(char[].class, boolean.class);
      newC.setAccessible(true);
    }
    catch (NoSuchMethodException e) {
      try {
        oldC = String.class.getDeclaredConstructor(int.class, int.class, char[].class);
        oldC.setAccessible(true);
      }
      catch (NoSuchMethodException ignored) {

      }
    }
    sharingCtr = newC;
    oldSharingCtr = oldC;
  }

  @NotNull
  public static String createShared(@NotNull char[] chars) {
    try {
      if (sharingCtr != null) {
        return sharingCtr.newInstance(chars, true);
      }
      if (oldSharingCtr != null) {
        return oldSharingCtr.newInstance(0, chars.length, chars);
      }
      return new String(chars);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
