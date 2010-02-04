/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyExpectedTypesProvider {
  private GroovyExpectedTypesProvider() {
  }

  public static TypeConstraint[] calculateTypeConstraints(GrExpression expression) {
    MyCalculator calculator = new MyCalculator(expression);
    ((GroovyPsiElement)expression.getParent()).accept(calculator);
    return calculator.getResult();
  }


  private static class MyCalculator extends GroovyElementVisitor {
    private TypeConstraint[] myResult;
    private final GrExpression myExpression;

    public MyCalculator(GrExpression expression) {
      myExpression = expression;
      myResult = new TypeConstraint[]{SubtypeConstraint.create("java.lang.Object", myExpression)};
    }

    public void visitReturnStatement(GrReturnStatement returnStatement) {
      GrParametersOwner parent = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, GrClosableBlock.class);
      if (parent instanceof GrMethod) {
        GrTypeElement typeElement = ((GrMethod)parent).getReturnTypeElementGroovy();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          myResult = new TypeConstraint[]{SubtypeConstraint.create(type)};
        }
      }
    }

    public void visitVariable(GrVariable variable) {
      if (myExpression.equals(variable.getInitializerGroovy())) {
        PsiType type = variable.getDeclaredType();
        if (type != null) {
          myResult = new TypeConstraint[]{new SubtypeConstraint(type, type)};
        }
      }
    }

    public void visitMethodCallExpression(GrMethodCallExpression methodCall) {
      if (myExpression.equals(methodCall.getInvokedExpression())) {
        myResult = new TypeConstraint[]{SubtypeConstraint.create("groovy.lang.Closure", methodCall)};
      }
    }

    public void visitIfStatement(GrIfStatement ifStatement) {
      if (myExpression.equals(ifStatement.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(ifStatement), PsiType.BOOLEAN)};
      }
    }

    public void visitWhileStatement(GrWhileStatement whileStatement) {
      if (myExpression.equals(whileStatement.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(whileStatement), PsiType.BOOLEAN)};
      }
    }

    public void visitTraditionalForClause(GrTraditionalForClause forClause) {
      if (myExpression.equals(forClause.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(forClause), PsiType.BOOLEAN)};
      }
    }

    public void visitArgumentList(GrArgumentList list) {
      PsiElement parent = list.getParent();
      List<TypeConstraint> constraints = new ArrayList<TypeConstraint>();
      if (parent instanceof GrCallExpression) {
        GroovyResolveResult[] variants = ((GrCallExpression)parent).getMethodVariants();
        int idx = list.getExpressionArgumentIndex(myExpression);
        for (GroovyResolveResult variant : variants) {
          PsiElement element = variant.getElement();
          PsiParameter[] parameters = null;
          if (element instanceof GrParametersOwner) {
            parameters = ((GrParametersOwner)element).getParameters();
          }
          else if (element instanceof PsiMethod) {
            parameters = ((PsiMethod)element).getParameterList().getParameters();
          }
          if (parameters == null || parameters.length <= idx) continue;
          PsiType parameterType = variant.getSubstitutor().substitute(parameters[idx].getType());
          constraints.add(SubtypeConstraint.create(parameterType));
        }
      }

      if (!constraints.isEmpty()) {
        myResult = constraints.toArray(new TypeConstraint[constraints.size()]);
      }
    }

    public void visitAssignmentExpression(GrAssignmentExpression expression) {
      GrExpression rValue = expression.getRValue();
      if (myExpression.equals(rValue)) {
        PsiType lType = expression.getLValue().getType();
        if (lType != null) {
          myResult = new TypeConstraint[]{SubtypeConstraint.create(lType)};
        }
      }
      else if (myExpression.equals(expression.getLValue())) {
        if (rValue != null) {
          PsiType rType = rValue.getType();
          if (rType != null) {
            myResult = new TypeConstraint[]{SupertypeConstraint.create(rType)};
          }
        }
      }
    }

    @Override
    public void visitThrowStatement(GrThrowStatement throwStatement) {
      final PsiClassType trowable = PsiType.getJavaLangTrowable(myExpression.getManager(), throwStatement.getResolveScope());
      myResult = new TypeConstraint[]{SubtypeConstraint.create(trowable)};
    }

    public TypeConstraint[] getResult() {
      return myResult;
    }
  }
}
