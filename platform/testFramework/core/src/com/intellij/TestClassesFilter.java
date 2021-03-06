// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class TestClassesFilter {
  public static final TestClassesFilter ALL_CLASSES = new TestClassesFilter() {
    @Override
    public boolean matches(String className, String moduleName) {
      return true;
    }

    @Override
    public String toString() {
      return "ALL_CLASSES";
    }
  };

  private static final boolean PATTERNS_ARE_ESCAPED = Boolean.getBoolean("intellij.build.test.patterns.escaped");

  public abstract boolean matches(String className, @Nullable String moduleName);

  public boolean matches(String className) { return matches(className, null); }

  protected static Pattern compilePattern(String filter) {
    if (!PATTERNS_ARE_ESCAPED) {
      filter = filter.replace("$", "\\$").replace(".", "\\.").replace("*", ".*");
    }
    return Pattern.compile(filter);
  }

  protected static boolean matchesAnyPattern(Collection<Pattern> patterns, String className) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
  }

  public static class And extends TestClassesFilter {
    private final TestClassesFilter first;
    private final TestClassesFilter second;

    public And(TestClassesFilter first, TestClassesFilter second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean matches(String className, @Nullable String moduleName) {
      return first.matches(className, moduleName) && second.matches(className, moduleName);
    }

    @Override
    public String toString() {
      return "TestClassesFilter.And{" + first + ", " + second + '}';
    }
  }
}
