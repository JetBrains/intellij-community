// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrInExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Max Medvedev
 */
public class GroovyInArgumentCheckInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Override
  protected String buildErrorString(Object... args) {
    PsiType ltype = (PsiType)args[0];
    PsiType rtype = (PsiType)args[1];
    return GroovyBundle.message("rtype.cannot.contain.ltype", ltype.getPresentableText(), rtype.getPresentableText());
  }

  private static class MyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitInExpression(@NotNull GrInExpression expression) {
      GrExpression leftOperand = expression.getLeftOperand();
      GrExpression rightOperand = expression.getRightOperand();
      if (rightOperand == null) return;

      PsiType ltype = leftOperand.getType();
      PsiType rtype = rightOperand.getType();
      if (ltype == null || rtype == null) return;

      PsiType component;

      if (rtype instanceof PsiArrayType) {
        component = ((PsiArrayType)rtype).getComponentType();
      }
      else if (InheritanceUtil.isInheritor(rtype, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        component = PsiUtil.substituteTypeParameter(rtype, CommonClassNames.JAVA_UTIL_COLLECTION, 0, false);
      }
      else {
        checkSimpleClasses(ltype, rtype, expression);
        return;
      }

      if (component == null) return;

      if (TypesUtil.isAssignableWithoutConversions(component, ltype)) return;

      registerError(expression, ltype, rtype);
    }

    private void checkSimpleClasses(PsiType ltype, PsiType rtype, GrBinaryExpression expression) {
      if (!(rtype instanceof PsiClassType)) return;
      if (!(ltype instanceof PsiClassType)) return;

      PsiClass lclass = ((PsiClassType)ltype).resolve();
      PsiClass rclass = ((PsiClassType)rtype).resolve();

      if (lclass == null || rclass == null) return;

      if (expression.getManager().areElementsEquivalent(lclass, rclass)) return;

      if (lclass.isInterface() || rclass.isInterface()) return;

      if (lclass.isInheritor(rclass, true) || rclass.isInheritor(lclass, true)) return;

      registerError(expression, ltype, rtype);
    }
  }
}
