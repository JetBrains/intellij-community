// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class WordPrefixMatcher implements Matcher {
  private final String[] myPatternWords;

  public WordPrefixMatcher(String pattern) {
    myPatternWords = NameUtil.nameToWords(pattern);
  }

  @Override
  public boolean matches(@NotNull String name) {
    String[] nameWords = NameUtil.nameToWords(name);
    return Arrays.stream(myPatternWords).allMatch(pw -> ContainerUtil.exists(nameWords, nw -> StringUtil.startsWithIgnoreCase(nw, pw)));
  }
}
