// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.Filter;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.launcher.MethodFilter;
import org.junit.platform.launcher.TagFilter;

/**
 * Filter options for JUnit5 tests.
 *
 * @see MethodFilter
 * @see TagFilter
 * @see PackageSelector
 * @see ClassNameFilter
 */
enum JUnit5FilterOption {

  includePackage("include-package"),
  excludePackage("exclude-package"),
  includeClassName("include-classname"),
  excludeClassName("exclude-classname"),
  includeMethodName("include-methodname"),
  excludeMethodName("exclude-methodname"),
  includeTag("include-tag"),
  excludeTag("exclude-tag");

  private final String value;

  JUnit5FilterOption(String value) {
    this.value = value;
  }

  Filter<?> toJunitFilter(String filterString) {
    return switch(this) {
      case includePackage -> PackageNameFilter.includePackageNames(filterString);
      case excludePackage -> PackageNameFilter.excludePackageNames(filterString);
      case includeClassName -> ClassNameFilter.includeClassNamePatterns(filterString);
      case excludeClassName -> ClassNameFilter.excludeClassNamePatterns(filterString);
      case includeMethodName -> MethodFilter.includeMethodNamePatterns(filterString);
      case excludeMethodName -> MethodFilter.excludeMethodNamePatterns(filterString);
      case includeTag -> TagFilter.includeTags(filterString);
      case excludeTag -> TagFilter.excludeTags(filterString);
    };
  }

  public static JUnit5FilterOption fromString(String value) {
    for (JUnit5FilterOption option : values()) {
      if (option.value.equals(value)) {
        return option;
      }
    }
    throw new IllegalStateException("Unexpected filter option value: " + value + ". Must be one of JUnit5FilterOption values.");
  }
}
