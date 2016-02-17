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

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import sun.reflect.ConstructorAccessor;

import java.lang.reflect.Constructor;

@SuppressWarnings("deprecation")
public class StringFactory {
  // String(char[], boolean). Works since JDK1.7, earlier JDKs have too slow reflection anyway
  private static final ConstructorAccessor ourConstructorAccessor;

  static {
    ConstructorAccessor constructorAccessor = null;
    try {
      Constructor<String> newC = String.class.getDeclaredConstructor(char[].class, boolean.class);
      constructorAccessor = ReflectionUtil.getConstructorAccessor(newC);
    }
    catch (Exception ignored) {
    }
    ourConstructorAccessor = constructorAccessor;
  }

  /**
   * @return new instance of String which backed by 'chars' array.
   *
   * CAUTION. EXTREMELY DANGEROUS.
   * DO NOT USE THIS METHOD UNLESS YOU ARE TOO DESPERATE
   */
  @NotNull
  public static String createShared(@NotNull char[] chars) {
    if (ourConstructorAccessor != null) {
      return ReflectionUtil.createInstanceViaConstructorAccessor(ourConstructorAccessor, chars, Boolean.TRUE);
    }
    return new String(chars);
  }
}