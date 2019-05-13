// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PatternListTestClassFilter extends TestClassesFilter {
  private final List<Pattern> myPatterns;

  public PatternListTestClassFilter(List<String> patterns) {
    myPatterns = compilePatterns(patterns);
  }

  @Override
  public boolean matches(String className, String moduleName) {
    return TestClassesFilter.matchesAnyPattern(myPatterns, className);
  }

  @Override
  public String toString() {
    return "PatternListTestClassFilter{myPatterns=" + myPatterns + '}';
  }
}
