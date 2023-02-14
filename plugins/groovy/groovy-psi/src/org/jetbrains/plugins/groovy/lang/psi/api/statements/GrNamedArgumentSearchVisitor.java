// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import java.util.*;

public class GrNamedArgumentSearchVisitor extends GroovyRecursiveElementVisitor {

  public static final NamedArgumentDescriptor CODE_NAMED_ARGUMENTS_DESCR = NamedArgumentDescriptor.SIMPLE_AS_LOCAL_VAR;

  private static final List<String> METHOD_NAMES = Arrays.asList("containsKey", "remove", "get");

  private final Map<String, NamedArgumentDescriptor> myResult = new HashMap<>();

  private final String myFirstArgumentName;

  public GrNamedArgumentSearchVisitor(String firstArgumentName) {
    myFirstArgumentName = firstArgumentName;
  }

  public Map<String, NamedArgumentDescriptor> getResult() {
    return myResult;
  }

  private void extractArguments(@NotNull GrArgumentList argumentList) {
    GrExpression[] expr = argumentList.getExpressionArguments();

    if (expr.length == 1 && expr[0] instanceof GrLiteral) {
      Object value = ((GrLiteral)expr[0]).getValue();
      if (value instanceof String s) {
        if (StringUtil.isJavaIdentifier(s)) {
          add((String)value);
        }
      }
    }
  }

  private void add(String refName) {
    myResult.put(refName, CODE_NAMED_ARGUMENTS_DESCR);
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    if (myFirstArgumentName.equals(referenceExpression.getReferenceName()) && !referenceExpression.isQualified()) {
      PsiElement parent = referenceExpression.getParent();

      if (parent instanceof GrReferenceExpression parentRef) {

        PsiElement parentParent = parentRef.getParent();

        if (parentParent instanceof GrMethodCallExpression) {
          if (METHOD_NAMES.contains(parentRef.getReferenceName())) {
            extractArguments(((GrMethodCallExpression)parentParent).getArgumentList());
          }
        }
        else {
          add(parentRef.getReferenceName());
        }
      }
      else if (parent instanceof GrIndexProperty indexProperty) {
        extractArguments(indexProperty.getArgumentList());
      }
    }

    super.visitReferenceExpression(referenceExpression);
  }

  public static Map<String, NamedArgumentDescriptor> find(GrVariable variable) {
    final GrExpression initializerGroovy = variable.getInitializerGroovy();

    if (!(initializerGroovy instanceof GrFunctionalExpression expression)) {
      return Collections.emptyMap();
    }

    final GrParameter[] parameters = expression.getAllParameters();
    if (parameters.length == 0) return Collections.emptyMap();

    GrParameter parameter = parameters[0];

    GrNamedArgumentSearchVisitor visitor = new GrNamedArgumentSearchVisitor(parameter.getName());
    expression.accept(visitor);
    return visitor.getResult();
  }
}
