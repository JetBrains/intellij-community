// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import org.ec4j.core.model.Glob;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class GlobVisibilityWorkaround {

  private GlobVisibilityWorkaround() {
  }

  @SuppressWarnings("unused") // Used in EditorConfigAutomatonBuilder.sanitizeGlob()
  public static String globToRegEx(String globString) {
    StringBuilder result = new StringBuilder();
    Glob glob = new Glob(globString);
    try {
      Method m = glob.getClass().getDeclaredMethod("convertGlobToRegEx", String.class, List.class, StringBuilder.class);
      m.setAccessible(true);
      m.invoke(glob, globString, new ArrayList<>(), result);
    }
    catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return result.toString();
  }
}