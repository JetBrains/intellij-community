// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.regex.Pattern;

public class PatternListTestClassFilter extends TestClassesFilter {
  private final List<Pattern> myPatterns;

  public PatternListTestClassFilter(List<String> patterns) {
    myPatterns = ContainerUtil.map(patterns, TestClassesFilter::compilePattern);
  }

  @Override
  public boolean matches(String className, String moduleName) {
    return TestClassesFilter.matchesAnyPattern(myPatterns, className);
  }

  @Override
  public String toString() {
    return "PatternListTestClassFilter{patterns=" + myPatterns + '}';
  }
}
