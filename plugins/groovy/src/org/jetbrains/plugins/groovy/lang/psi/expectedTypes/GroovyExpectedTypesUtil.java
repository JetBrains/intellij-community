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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author ven
 */
public class GroovyExpectedTypesUtil {
  public static TypeConstraint[] calculateTypeConstraints(GrExpression expression) {
    MyCalculator calculator = new MyCalculator(expression);
    ((GroovyPsiElement) expression.getParent()).accept(calculator);
    return calculator.getResult();
  }


  private static class MyCalculator extends GroovyElementVisitor {
    private TypeConstraint[] myResult;
    private GrExpression myExpression;

    public MyCalculator(GrExpression expression) {
      myExpression = expression;
    }

    public void visitElement(GroovyPsiElement element) {
      makeDefault(element);
    }

    public void visitReturnStatement(GrReturnStatement returnStatement) {
      GrParametersOwner parent = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, GrClosableBlock.class);
      if (parent instanceof GrMethod) {
        GrTypeElement typeElement = ((GrMethod) parent).getReturnTypeElementGroovy();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          myResult = new TypeConstraint[] {SubtypeConstraint.create(type)};
          return;
        }
      }
      makeDefault(returnStatement);
    }

    public void visitVariable(GrVariable variable) {
      if (myExpression.equals(variable.getInitializerGroovy())) {
        PsiType type = variable.getTypeGroovy();
        if (type != null) {
          myResult = new TypeConstraint[] {new SubtypeConstraint(type, type)};
          return;
        }
      }
      makeDefault(variable);
    }

    public void visitAssignmentExpression(GrAssignmentExpression expression) {
      GrExpression rValue = expression.getRValue();
      if (myExpression.equals(rValue)) {
        PsiType lType = expression.getLValue().getType();
        if (lType == null) makeDefault(expression);
        else {
          myResult = new TypeConstraint[]{SubtypeConstraint.create(lType)};
        }
      } else if (myExpression.equals(expression.getLValue())) {
        if (rValue == null) makeDefault(expression);
        else {
          PsiType rType = rValue.getType();
          if (rType == null) makeDefault(expression);
          else {
            myResult = new TypeConstraint[]{SupertypeConstraint.create(rType)};
          }
        }
      }
    }

    private void makeDefault(GroovyPsiElement element) {
      myResult = new TypeConstraint[]{
          SubtypeConstraint.create("java.lang.Object", element)
      };
    }

    public TypeConstraint[] getResult() {
      return myResult;
    }
  }
}
