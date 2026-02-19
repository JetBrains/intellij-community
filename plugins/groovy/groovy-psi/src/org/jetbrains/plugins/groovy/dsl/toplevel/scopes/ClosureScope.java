// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel.scopes;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.PlaceContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GrAnnotationPattern;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class ClosureScope extends Scope {
  public ClosureScope(Map args) {
    Boolean isArgument = args == null ? null : (Boolean)args.get("isArgument");
    arg = isArgument != null && isArgument;
    methodName = args == null ? null : (String)args.get("methodName");
    annotationName = args == null ? null : (String)args.get("annotationName");
  }

  @Override
  public List<ContextFilter> createFilters(Map args) {
    List<ContextFilter> result = new ArrayList<>();
    result.add(new PlaceContextFilter(PlatformPatterns.psiElement().inside(GrClosableBlock.class)));

    final ClosureScope scope = (ClosureScope)args.get("scope");

    if (scope.isArg()) {
      result.add(new PlaceContextFilter(
        PlatformPatterns.psiElement().inside(
        PlatformPatterns.psiElement(GrClosableBlock.class)
          .withParent(StandardPatterns.or(
            PlatformPatterns.psiElement(GrCall.class),
            PlatformPatterns.psiElement(GrArgumentList.class).withParent(GrCall.class))))));
    }

    if (scope.getMethodName() != null) {
      result.add(new PlaceContextFilter(PlatformPatterns.psiElement().inside(
        PlatformPatterns.psiElement(GrClosableBlock.class).withParent(
          StandardPatterns.or(GroovyPatterns.methodCall().withMethodName(scope.getMethodName()),
                              PlatformPatterns.psiElement(GrArgumentList.class)
                                .withParent(GroovyPatterns.methodCall().withMethodName(scope.getMethodName())))))));
    }

    if (scope.getAnnotationName() != null) {
      result.add(new PlaceContextFilter(PlatformPatterns.psiElement().inside(
        PlatformPatterns.psiElement(GrClosableBlock.class)
          .inside(GrAnnotationPattern.annotation().withQualifiedName(scope.getAnnotationName())))));
    }

    // Enhance only unqualified expressions
    String ctype = (String)args.get("ctype");
    if (ctype == null || ctype.isEmpty()) {
      result.add(ClassContextFilter.subtypeOf(GroovyCommonClassNames.GROOVY_LANG_CLOSURE));
    }

    return result;
  }

  public final boolean getArg() {
    return arg;
  }

  public final boolean isArg() {
    return arg;
  }

  public final String getMethodName() {
    return methodName;
  }

  public final String getAnnotationName() {
    return annotationName;
  }

  private final boolean arg;
  private final String methodName;
  private final String annotationName;
}
