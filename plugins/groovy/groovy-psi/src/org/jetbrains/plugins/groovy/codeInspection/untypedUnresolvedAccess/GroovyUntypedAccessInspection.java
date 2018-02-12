/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
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
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Access to untyped expression";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return "Cannot determine type of '#ref'";
  }
}
