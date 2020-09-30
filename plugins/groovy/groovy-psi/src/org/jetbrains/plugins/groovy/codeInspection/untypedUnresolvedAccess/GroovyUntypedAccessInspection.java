// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyUntypedAccessInspection extends BaseInspection {

  @Override
  @NotNull
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression refExpr) {
        super.visitReferenceExpression(refExpr);

        if (PsiUtil.isThisOrSuperRef(refExpr)) return;

        GroovyResolveResult resolveResult = refExpr.advancedResolve();

        PsiElement resolved = resolveResult.getElement();
        if (resolved != null) {
          if (GrHighlightUtil.isDeclarationAssignment(refExpr) || resolved instanceof PsiPackage) return;
        }
        else {
          GrExpression qualifier = refExpr.getQualifierExpression();
          if (qualifier == null && GrHighlightUtil.isDeclarationAssignment(refExpr)) return;
        }

        final PsiType refExprType = refExpr.getType();
        if (refExprType == null) {
          if (resolved != null) {
            registerError(refExpr);
          }
        }
        else if (refExprType instanceof PsiClassType && ((PsiClassType)refExprType).resolve() == null) {
          registerError(refExpr);
        }
      }
    };
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.cannot.determine.type.ref");
  }
}
