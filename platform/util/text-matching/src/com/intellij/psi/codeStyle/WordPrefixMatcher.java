// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class WordPrefixMatcher implements Matcher {
  private final String[] myPatternWords;

  public WordPrefixMatcher(String pattern) {
    String fixedLayout = FixingLayoutMatcher.fixLayout(pattern);
    myPatternWords = NameUtil.nameToWords(fixedLayout != null && !fixedLayout.equals(pattern) ? fixedLayout : pattern);
  }

  @Override
  public boolean matches(@NotNull String name) {
    String[] nameWords = NameUtil.nameToWords(name);
    return Arrays.stream(myPatternWords).allMatch(pw -> Arrays.stream(nameWords).anyMatch(nw -> StringUtilRt.startsWithIgnoreCase(nw, pw)));
  }
}
