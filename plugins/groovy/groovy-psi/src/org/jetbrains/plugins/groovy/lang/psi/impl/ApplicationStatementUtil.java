// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author Max Medvedev
 */
public final class ApplicationStatementUtil {

  public static GrExpression convertToMethodCallExpression(GrExpression expr) {
    final Project project = expr.getProject();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    boolean copied = false;
    if (expr instanceof GrApplicationStatement) {
      expr = convertAppInternal(factory, (GrApplicationStatement)expr);
      copied = true;
    }

    if (expr instanceof GrReferenceExpression &&
        ((GrReferenceExpression)expr).getDotToken() == null &&
        ((GrReferenceExpression)expr).getQualifier() != null) {
      expr = convertRefInternal(factory, ((GrReferenceExpression)expr));
      copied = true;
    }


    if (!shouldManage(expr)) return expr;
    if (!copied) expr = (GrExpression)expr.copy();

    for (PsiElement child = expr.getFirstChild(); child != null; child = child.getFirstChild()) {
      if (child instanceof GrApplicationStatement) {
        child = child.replace(convertAppInternal(factory, (GrApplicationStatement)child));
      }
      else if (child instanceof GrReferenceExpression &&
               ((GrReferenceExpression)child).getDotToken() == null &&
               ((GrReferenceExpression)child).getQualifier() != null) {
        child = child.replace(convertRefInternal(factory, ((GrReferenceExpression)child)));
      }
    }

    return expr;
  }

  private static boolean shouldManage(GrExpression expr) {
    for (PsiElement child = expr.getFirstChild(); child != null; child = child.getFirstChild()) {
      if (child instanceof GrApplicationStatement) {
        return true;
      }
      else if (child instanceof GrReferenceExpression &&
               ((GrReferenceExpression)child).getDotToken() == null &&
               ((GrReferenceExpression)child).getQualifier() != null) {
        return true;
      }
    }
    return false;
  }

  private static GrReferenceExpression convertRefInternal(GroovyPsiElementFactory factory, GrReferenceExpression ref) {
    ref.addAfter(factory.createDotToken("."), ref.getQualifier());
    return ref;
  }

  private static GrMethodCallExpression convertAppInternal(GroovyPsiElementFactory factory, GrApplicationStatement app) {
    final GrCommandArgumentList list = app.getArgumentList();

    final GrMethodCallExpression prototype = (GrMethodCallExpression)factory.createExpressionFromText("foo()");
    prototype.getInvokedExpression().replace(app.getInvokedExpression());
    final GrArgumentList pList = prototype.getArgumentList();

    final PsiElement anchor = pList.getRightParen();
    for (GroovyPsiElement arg : list.getAllArguments()) {
      pList.addBefore(arg, anchor);
    }

    return prototype;
  }
}
