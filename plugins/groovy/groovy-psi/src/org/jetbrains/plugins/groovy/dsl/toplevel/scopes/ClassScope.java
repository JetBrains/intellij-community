// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel.scopes;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.StringPattern;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.PlaceContextFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class ClassScope extends Scope {
  private final String namePattern;

  public ClassScope(Map args) {
    String name = args == null ? null : (String)args.get("name");
    namePattern = name != null ? name : ".*";
  }

  @Override
  public List<ContextFilter> createFilters(Map args) {
    List<ContextFilter> result = new ArrayList<>();

    if (namePattern != null && !namePattern.isEmpty()) {
      StringPattern match = StandardPatterns.string().matches(namePattern);
      result.add(new PlaceContextFilter(PlatformPatterns.psiElement().inside(
        StandardPatterns.or(PsiJavaPatterns.psiClass().withQualifiedName(match), PsiJavaPatterns.psiClass().withName(match)))));
    }

    return result;
  }
}
