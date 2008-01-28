/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ven
 */
public class GroovyExpectedTypesUtil {
  public static ExpectedTypeInfo[] calculateExpectedType(GrExpression expression) {
    ((GroovyPsiElement) expression.getParent()).accept(new MyCalculator(expression));
    return new ExpectedTypeInfo[0]; //todo
  }


  private static class MyCalculator extends GroovyElementVisitor {
    private ExpectedTypeInfo[] myResult;
    private GrExpression myExpression;

    public MyCalculator(GrExpression expression) {
      myExpression = expression;
    }

    public void visitElement(GroovyPsiElement element) {
      makeDefault(element);
    }

    public void visitAssignmentExpression(GrAssignmentExpression expression) {
      GrExpression rValue = expression.getRValue();
      if (myExpression.equals(rValue)) {
        PsiType lType = expression.getLValue().getType();
        if (lType == null) makeDefault(expression);
        else {
          myResult = new ExpectedTypeInfo[]{SubtypeConstraint.create(lType)};
        }
      } else if (myExpression.equals(expression.getLValue())) {
        if (rValue == null) makeDefault(expression);
        else {
          PsiType rType = rValue.getType();
          if (rType == null) makeDefault(expression);
          else {
            myResult = new ExpectedTypeInfo[]{SupertypeConstraint.create(rType)};
          }
        }
      }
    }

    private void makeDefault(GroovyPsiElement element) {
      myResult = new ExpectedTypeInfo[]{
          SubtypeConstraint.create("java.lang.Object", element)
      };
    }

    public ExpectedTypeInfo[] getResult() {
      return myResult;
    }
  }
}
