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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
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
    return GroovyInspectionBundle.message("rtype.cannot.contain.ltype", ltype.getPresentableText(), rtype.getPresentableText());

  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incompatible 'in' argument types";
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);

      if (expression.getOperationTokenType() != GroovyTokenTypes.kIN) return;

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
      
      if (TypesUtil.isAssignableWithoutConversions(component, ltype, expression)) return;

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

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
