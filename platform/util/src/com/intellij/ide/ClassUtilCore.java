/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide;

import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class ClassUtilCore {
  @NonNls static final String FILE_CACHE = "fileCache";
  @NonNls static final String URL_CACHE = "urlCache";// See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4167874

  public static void clearJarURLCache() {
    try {
      Class jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

      clearMap(jarFileFactory.getDeclaredField(FILE_CACHE));
      clearMap(jarFileFactory.getDeclaredField(URL_CACHE));
    }
    catch (Exception e) {
      System.out.println("Failed to clear URL cache");
      e.printStackTrace();
      // Do nothing.
    }
  }

  private static void clearMap(Field cache) throws IllegalAccessException {
    cache.setAccessible(true);
    if ((cache.getModifiers() & Modifier.FINAL) == 0) {
      cache.set(null, new HashMap());
    }
    else {
      Map map = (Map)cache.get(null);
      map.clear();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isLoadingOfExternalPluginsDisabled() {
    return !"true".equalsIgnoreCase(System.getProperty("idea.plugins.load", "true"));
  }
}
