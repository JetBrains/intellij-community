// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NameListTestClassFilter extends TestClassesFilter {
  private final Set<String> myClassNames;

  public NameListTestClassFilter(List<String> classNames) {
    myClassNames = new LinkedHashSet<>(classNames);
  }

  @Override
  public boolean matches(String className, String moduleName) {
    return myClassNames.contains(className);
  }

  @Override
  public String toString() {
    return "NameListTestClassFilter{names=" + myClassNames + '}';
  }
}
