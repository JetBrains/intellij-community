// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.util.BitUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class ClassUtilCore {
  public static void clearJarURLCache() {
    try {
      Class jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

      clearMap(jarFileFactory.getDeclaredField("fileCache"));
      clearMap(jarFileFactory.getDeclaredField("urlCache"));
    }
    catch (Exception ignore) {
      // Do nothing.
    }
  }

  private static void clearMap(Field cache) throws IllegalAccessException {
    cache.setAccessible(true);
    if (!BitUtil.isSet(cache.getModifiers(), Modifier.FINAL)) {
      cache.set(null, new HashMap());
    }
    else {
      Map map = (Map)cache.get(null);
      map.clear();
    }
  }
}