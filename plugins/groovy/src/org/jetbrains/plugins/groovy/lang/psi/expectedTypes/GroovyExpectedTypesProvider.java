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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class GroovyExpectedTypesProvider {
  private static final Key<CachedValue<TypeConstraint[]>> CACHED_EXPECTED_TYPES = Key.create("CACHED_EXPECTED_TYPES");

  private GroovyExpectedTypesProvider() {
  }

  public static TypeConstraint[] calculateTypeConstraints(@NotNull final GrExpression expression) {
    CachedValue<TypeConstraint[]> cached = expression.getUserData(CACHED_EXPECTED_TYPES);
    if (cached == null) {
      expression.putUserData(CACHED_EXPECTED_TYPES, cached = CachedValuesManager.getManager(expression.getProject()).createCachedValue(new CachedValueProvider<TypeConstraint[]>() {
        public Result<TypeConstraint[]> compute() {
          MyCalculator calculator = new MyCalculator(expression);
          ((GroovyPsiElement)expression.getParent()).accept(calculator);
          final TypeConstraint[] result = calculator.getResult();

          List<TypeConstraint> custom = new ArrayList<TypeConstraint>();
          for (GroovyExpectedTypesContributor contributor : GroovyExpectedTypesContributor.EP_NAME.getExtensions()) {
            custom.addAll(contributor.calculateTypeConstraints(expression));
          }

          if (!custom.isEmpty()) {
            custom.addAll(0, Arrays.asList(result));
            return Result.create(custom.toArray(new TypeConstraint[custom.size()]), PsiModificationTracker.MODIFICATION_COUNT);
          }

          return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        }
      }, false));
    }
    return cached.getValue();
  }

  public static Set<PsiType> getDefaultExpectedTypes(@NotNull GrExpression element) {
    final LinkedHashSet<PsiType> result = new LinkedHashSet<PsiType>();
    for (TypeConstraint constraint : calculateTypeConstraints(element)) {
      result.add(constraint.getDefaultType());
    }
    return result;
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
      final GrExpression invokedExpression = methodCall.getInvokedExpression();
      if (myExpression.equals(invokedExpression)) {
        myResult = new TypeConstraint[]{SubtypeConstraint.create("groovy.lang.Closure", methodCall)};
        return;
      }

      final List<GrClosableBlock> closureArgs = Arrays.asList(methodCall.getClosureArguments());
      //noinspection SuspiciousMethodCalls
      final int closureIndex = closureArgs.indexOf(myExpression);
      if (closureIndex >= 0) {
        List<TypeConstraint> constraints = new ArrayList<TypeConstraint>();
        for (GroovyResolveResult variant : ResolveUtil.getCallVariants(myExpression)) {
          addConstraintsFromMap(constraints,
                                GrClosureSignatureUtil.mapArgumentsToParameters(variant, methodCall.getArgumentList(), methodCall, methodCall.getClosureArguments(),
                                                                                true));
        }
        if (!constraints.isEmpty()) {
          myResult = constraints.toArray(new TypeConstraint[constraints.size()]);
        }

      }
    }

    @Override
    public void visitOpenBlock(GrOpenBlock block) {
      if (block.getParent() instanceof PsiMethod) {
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0 && myExpression.equals(statements[statements.length - 1])) {
          final PsiType type = ((PsiMethod)block.getParent()).getReturnType();
          if (type != null) {
            myResult = new TypeConstraint[]{new SubtypeConstraint(type, type)};
          }
        }
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
      List<TypeConstraint> constraints = new ArrayList<TypeConstraint>();
      for (GroovyResolveResult variant : ResolveUtil.getCallVariants(list)) {
        addConstraintsFromMap(constraints,
                              GrClosureSignatureUtil.mapArgumentsToParameters(variant, list, list, GrClosableBlock.EMPTY_ARRAY, true));
      }
      if (!constraints.isEmpty()) {
        myResult = constraints.toArray(new TypeConstraint[constraints.size()]);
      }
    }

    private void addConstraintsFromMap(List<TypeConstraint> constraints,
                                       Map<GrExpression, Pair<PsiParameter, PsiType>> map) {
      if (map != null) {
        final Pair<PsiParameter, PsiType> pair = map.get(myExpression);
        if (pair != null) {
          constraints.add(SubtypeConstraint.create(pair.second));
        }
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

    @Override
    public void visitUnaryExpression(final GrUnaryExpression expression) {
      TypeConstraint constraint = new TypeConstraint(PsiType.INT) {
        @Override
        public boolean satisfied(PsiType type, PsiManager manager, GlobalSearchScope scope) {
          return TypesUtil
            .getOverloadedOperatorCandidates(TypesUtil.boxPrimitiveType(type, manager, scope), expression.getOperationTokenType(),
                                             expression, PsiType.EMPTY_ARRAY).length > 0;
        }

        @Override
        public PsiType getDefaultType() {
          return PsiType.INT;
        }
      };
      myResult = new TypeConstraint[]{constraint};
    }

    public TypeConstraint[] getResult() {
      return myResult;
    }
  }
}
