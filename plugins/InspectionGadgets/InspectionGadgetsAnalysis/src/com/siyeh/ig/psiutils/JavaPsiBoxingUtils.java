// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class JavaPsiBoxingUtils {

  private static final @NonNls Map<String, String> parseMethodsMap = new HashMap<>();

  static {
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_INTEGER, "parseInt");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_LONG, "parseLong");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "parseBoolean");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_SHORT, "parseShort");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_BYTE, "parseByte");
  }

  /**
   * Get parse method name without qualifier for given full class name.
   */
  @Nullable
  public static String getParseMethod(@Nullable String className) {
    return parseMethodsMap.get(className);
  }
}
