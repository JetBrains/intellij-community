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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

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
          final PsiElement parent = expression.getParent();
          if (parent instanceof GroovyPsiElement) {
            ((GroovyPsiElement)parent).accept(calculator);
          }
          else {
            parent.accept(new GroovyPsiElementVisitor(calculator));
          }
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

  @Nullable
  public static PsiType getExpectedClosureReturnType(GrClosableBlock closure) {
  final Set<PsiType> expectedTypes = getDefaultExpectedTypes(closure);

  List<PsiType> expectedReturnTypes = new ArrayList<PsiType>();
  for (PsiType expectedType : expectedTypes) {
    if (!(expectedType instanceof PsiClassType)) return null;

    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)expectedType).resolveGenerics();
    final PsiClass resolved = resolveResult.getElement();
    if (resolved == null || !(GroovyCommonClassNames.GROOVY_LANG_CLOSURE.equals(resolved.getQualifiedName()))) return null;

    final PsiTypeParameter[] typeParameters = resolved.getTypeParameters();
    if (typeParameters.length != 1) return null;

    final PsiTypeParameter expected = typeParameters[0];
    final PsiType expectedReturnType = resolveResult.getSubstitutor().substitute(expected);
    if (expectedReturnType == PsiType.VOID || expectedReturnType == null) return null;

    expectedReturnTypes.add(expectedReturnType);
  }

  return TypesUtil.getLeastUpperBoundNullable(expectedReturnTypes, closure.getManager());
}


  private static class MyCalculator extends GroovyElementVisitor {
    private TypeConstraint[] myResult;
    private final GrExpression myExpression;

    public MyCalculator(GrExpression expression) {
      myExpression = (GrExpression)PsiUtil.skipParentheses(expression, true);
      myResult = TypeConstraint.EMPTY_ARRAY;
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
        PsiType type = variable.getType();
        myResult = new TypeConstraint[]{new SubtypeConstraint(type, type)};
      }
    }

    @Override
    public void visitNamedArgument(GrNamedArgument argument) {
      PsiElement pparent = argument.getParent().getParent();
      if (pparent instanceof GrCall && resolvesToDefaultConstructor(((GrCall)pparent))) {
        GrArgumentLabel label = argument.getLabel();
        if (label != null) {
          PsiElement resolved = label.resolve();
          if (resolved instanceof PsiField) {
            PsiType type = ((PsiField)resolved).getType();
            myResult = new TypeConstraint[]{new SubtypeConstraint(type, type)};
          }
          else if (resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)resolved)) {
            PsiType type = ((PsiMethod)resolved).getParameterList().getParameters()[0].getType();
            myResult = new TypeConstraint[]{new SubtypeConstraint(type,type)};
          }
        }
      }
    }

    private static boolean resolvesToDefaultConstructor(GrCall call) {
      PsiMethod method = call.resolveMethod();
      if (method != null && method.isConstructor() && method.getParameterList().getParametersCount() == 0) return true;

      if (call instanceof GrConstructorCall) {
        PsiElement resolved = PsiImplUtil.extractUniqueResult(((GrConstructorCall)call).multiResolveClass()).getElement();
        if (resolved instanceof PsiClass) return true;
      }

      return false;
    }

    public void visitMethodCallExpression(GrMethodCallExpression methodCall) {
      final GrExpression invokedExpression = methodCall.getInvokedExpression();
      if (myExpression.equals(invokedExpression)) {
        myResult = new TypeConstraint[]{SubtypeConstraint.create(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, methodCall)};
        return;
      }

      final GrClosableBlock[] closureArgs = methodCall.getClosureArguments();
      //noinspection SuspiciousMethodCalls
      final int closureIndex = Arrays.asList(closureArgs).indexOf(myExpression);
      if (closureIndex >= 0) {
        List<TypeConstraint> constraints = new ArrayList<TypeConstraint>();
        for (GroovyResolveResult variant : ResolveUtil.getCallVariants(myExpression)) {
          final GrArgumentList argumentList = methodCall.getArgumentList();
          final GrNamedArgument[] namedArgs = argumentList == null ? GrNamedArgument.EMPTY_ARRAY : argumentList.getNamedArguments();
          final GrExpression[] expressionArgs = argumentList == null ? GrExpression.EMPTY_ARRAY : argumentList.getExpressionArguments();
          addConstraintsFromMap(constraints,
                                GrClosureSignatureUtil.mapArgumentsToParameters(variant, methodCall, true, true, namedArgs, expressionArgs,
                                                                                closureArgs),
                                closureIndex == closureArgs.length - 1);
        }
        if (!constraints.isEmpty()) {
          myResult = constraints.toArray(new TypeConstraint[constraints.size()]);
        }

      }
    }

    @Override
    public void visitOpenBlock(GrOpenBlock block) {
      final GrStatement[] statements = block.getStatements();
      if (statements.length > 0 && myExpression.equals(statements[statements.length - 1])) {
        checkExitPoint();
      }
    }

    public void visitIfStatement(GrIfStatement ifStatement) {
      if (myExpression.equals(ifStatement.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(ifStatement), PsiType.BOOLEAN)};
      }
      else if (myExpression.equals(ifStatement.getThenBranch()) || myExpression.equals(ifStatement.getElseBranch())) {
        checkExitPoint();
      }
    }

    private void checkExitPoint() {
      final PsiElement element = PsiTreeUtil.getParentOfType(myExpression, PsiMethod.class, GrClosableBlock.class);
      if (element instanceof GrMethod) {
        final GrMethod method = (GrMethod)element;
        ControlFlowUtils.visitAllExitPoints(method.getBlock(), new ControlFlowUtils.ExitPointVisitor() {
          @Override
          public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
            if (returnValue == myExpression) {
              final PsiType returnType = method.getReturnType();
              if (returnType != null) {
                myResult = new TypeConstraint[]{new SubtypeConstraint(returnType, returnType)};
              }
              return false;
            }
            return true;
          }
        });
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
        final GrExpression[] arguments = list.getExpressionArguments();
        addConstraintsFromMap(constraints,
                              GrClosureSignatureUtil.mapArgumentsToParameters(variant, list, true, true,
                                                                              list.getNamedArguments(),
                                                                              list.getExpressionArguments(),
                                                                              GrClosableBlock.EMPTY_ARRAY
                              ),
                              Arrays.asList(arguments).indexOf(myExpression) == arguments.length - 1);
      }
      if (!constraints.isEmpty()) {
        myResult = constraints.toArray(new TypeConstraint[constraints.size()]);
      }
    }

    @Override
    public void visitBinaryExpression(GrBinaryExpression expression) {
      final IElementType type = expression.getOperationTokenType();
      final GrExpression left = expression.getLeftOperand();
      final GrExpression right = expression.getRightOperand();


      if (type == mREGEX_FIND || type == mREGEX_MATCH) {
        final PsiClassType string = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, expression);
        myResult = new TypeConstraint[]{new SubtypeConstraint(string, string)};
        return;
      }

      final GrExpression other = myExpression == left ? right : left;
      final PsiType otherType = other != null ? other.getType() : null;

      if (otherType == null) return;

      if (type== mPLUS && otherType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        final PsiClassType obj = TypesUtil.getJavaLangObject(expression);
        myResult = new TypeConstraint[]{new SubtypeConstraint(obj, obj)};
        return;
      }

      myResult = new TypeConstraint[]{new SubtypeConstraint(otherType, otherType)};
    }

    @Override
    public void visitInstanceofExpression(GrInstanceOfExpression expression) {
      final GrExpression operand = expression.getOperand();
      final GrTypeElement typeElement = expression.getTypeElement();
      if (typeElement == null) return;

      if (myExpression == operand) {
        final PsiType type = typeElement.getType();
        myResult = new TypeConstraint[]{new SupertypeConstraint(type, type)};
      }
    }

    private void addConstraintsFromMap(List<TypeConstraint> constraints,
                                       Map<GrExpression, Pair<PsiParameter, PsiType>> map,
                                       boolean isLast) {
      if (map == null) return;

      final Pair<PsiParameter, PsiType> pair = map.get(myExpression);
      if (pair == null) return;

      final PsiType type = pair.second;
      if (type == null) return;

      constraints.add(SubtypeConstraint.create(type));

      if (type instanceof PsiArrayType && isLast) {
        constraints.add(SubtypeConstraint.create(((PsiArrayType)type).getComponentType()));
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
      final PsiClassType throwable = PsiType.getJavaLangThrowable(myExpression.getManager(), throwStatement.getResolveScope());
      myResult = new TypeConstraint[]{SubtypeConstraint.create(throwable)};
    }

    @Override
    public void visitUnaryExpression(final GrUnaryExpression expression) {
      TypeConstraint constraint = new TypeConstraint(PsiType.INT) {
        @Override
        public boolean satisfied(PsiType type, PsiManager manager, GlobalSearchScope scope) {
          final PsiType boxed = TypesUtil.boxPrimitiveType(type, manager, scope);
          final IElementType opToken = expression.getOperationTokenType();
          final GroovyResolveResult[] candidates =
            TypesUtil.getOverloadedUnaryOperatorCandidates(boxed, opToken, expression, PsiType.EMPTY_ARRAY);
          return candidates.length > 0;
        }

        @NotNull
        @Override
        public PsiType getDefaultType() {
          return PsiType.INT;
        }
      };
      myResult = new TypeConstraint[]{constraint};
    }

    @Override
    public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof GroovyPsiElement) {
        ((GroovyPsiElement)parent).accept(this);
      }
      else {
        parent.accept(new GroovyPsiElementVisitor(this));
      }
    }

    @Override
    public void visitListOrMap(GrListOrMap listOrMap) {
      if (listOrMap.isMap()) return;
      final TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(listOrMap);
      List<PsiType> result=new ArrayList<PsiType>(constraints.length);
      for (TypeConstraint constraint : constraints) {
        if (constraint instanceof SubtypeConstraint) {
          final PsiType type = constraint.getType();
          final PsiType iterable = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, true);
          if (iterable != null) {
            result.add(iterable);
          }
        }
      }
      if (result.size()==0) {
      myResult = TypeConstraint.EMPTY_ARRAY;
      }
      else {
        myResult = new TypeConstraint[result.size()];
        for (int i = 0; i < result.size(); i++) {
          final PsiType type = result.get(i);
          if (type!=null) {
            myResult[i] = SubtypeConstraint.create(type);
          }
        }
      }
    }

    @Override
    public void visitCaseLabel(GrCaseLabel caseLabel) {
      final PsiElement parent = caseLabel.getParent().getParent();
      assert parent instanceof GrSwitchStatement : parent + " of class " + parent.getClass();
      final GrExpression condition = ((GrSwitchStatement)parent).getCondition();
      if (condition == null) return;

      final PsiType type = condition.getType();
      if (type == null) return;

      myResult = new TypeConstraint[]{SubtypeConstraint.create(type)};
    }

    @Override
    public void visitSwitchStatement(GrSwitchStatement switchStatement) {
      final GrCaseSection[] sections = switchStatement.getCaseSections();
      List<PsiType> types = new ArrayList<PsiType>(sections.length);
      for (GrCaseSection section : sections) {
        final GrExpression value = section.getCaseLabel().getValue();
        final PsiType type = value != null ? value.getType() : null;
        if (type != null) types.add(type);
      }

      final PsiType upperBoundNullable = TypesUtil.getLeastUpperBoundNullable(types, switchStatement.getManager());
      if (upperBoundNullable == null) return;

      myResult = new TypeConstraint[]{SubtypeConstraint.create(upperBoundNullable)};
    }

    public TypeConstraint[] getResult() {
      return myResult;
    }
  }
}
