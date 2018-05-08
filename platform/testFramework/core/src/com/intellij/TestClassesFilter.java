// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

  public abstract boolean matches(String className, @Nullable String moduleName);
  
  public boolean matches(String className) { return matches(className, null); }

  protected static ArrayList<Pattern> compilePatterns(Collection<String> filterList) {
    ArrayList<Pattern> patterns = new ArrayList<>();
    for (String aFilter : filterList) {
      String filter = aFilter.trim();
      if (filter.length() == 0) continue;
      filter = filter.replaceAll("\\*", ".\\*");
      Pattern pattern = Pattern.compile(filter);
      patterns.add(pattern);
    }
    return patterns;
  }

  protected static boolean matchesAnyPattern(Collection<Pattern> patterns, String className) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(className).matches()) {
        return true;
      }
    }
    return false;
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
      return first.matches(className, moduleName) &&
             second.matches(className, moduleName);
    }

    @Override
    public String toString() {
      return "AndTestClassesFilter{" +
             "first: " + first.toString() + "," +
             "second: " + second.toString() + '}';
    }
  }
}
