// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author maxim
 */
public final class MatchUtils {

  public static boolean compareWithNoDifferenceToPackage(String typeImage, String typeImage2) {
    return compareWithNoDifferenceToPackage(typeImage, typeImage2, false);
  }

  public static boolean compareWithNoDifferenceToPackage(String typeImage, @NonNls String typeImage2, boolean ignoreCase) {
    if (typeImage == null || typeImage2 == null) return typeImage == typeImage2;
    final boolean endsWith = ignoreCase ? StringUtil.endsWithIgnoreCase(typeImage2, typeImage) : typeImage2.endsWith(typeImage);
    return endsWith && (
      typeImage.length() == typeImage2.length() ||
      typeImage2.charAt(typeImage2.length()-typeImage.length() - 1)=='.' // package separator
    );
  }
}
