/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ven
 */
public class GroovyExpectedTypesProvider {

  public static TypeConstraint[] calculateTypeConstraints(@NotNull final GrExpression expression) {
    return TypeInferenceHelper.getCurrentContext().getCachedValue(expression, () -> {
        MyCalculator calculator = new MyCalculator(expression);
        final PsiElement parent = expression.getParent();
        if (parent instanceof GroovyPsiElement) {
          ((GroovyPsiElement)parent).accept(calculator);
        }
        else {
          parent.accept(new GroovyPsiElementVisitor(calculator));
        }
        final TypeConstraint[] result = calculator.getResult();

        List<TypeConstraint> custom = ContainerUtil.newArrayList();
        for (GroovyExpectedTypesContributor contributor : GroovyExpectedTypesContributor.EP_NAME.getExtensions()) {
          custom.addAll(contributor.calculateTypeConstraints(expression));
        }

        if (!custom.isEmpty()) {
          custom.addAll(0, Arrays.asList(result));
          return custom.toArray(new TypeConstraint[custom.size()]);
        }

        return result;
      });
  }

  public static List<PsiType> getDefaultExpectedTypes(@NotNull GrExpression element) {
    TypeConstraint[] constraints = calculateTypeConstraints(element);
    return ContainerUtil.map(constraints, constraint -> constraint.getDefaultType());
  }

  private static class MyCalculator extends GroovyElementVisitor {
    private TypeConstraint[] myResult;
    private final GrExpression myExpression;

    public MyCalculator(GrExpression expression) {
      myExpression = (GrExpression)PsiUtil.skipParentheses(expression, true);
      myResult = TypeConstraint.EMPTY_ARRAY;
    }

    @Override
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

    @Override
    public void visitVariable(GrVariable variable) {
      if (myExpression.equals(variable.getInitializerGroovy())) {
        PsiType type = variable.getType();
        myResult = createSimpleSubTypeResult(type);
      }
    }

    @Override
    public void visitNamedArgument(GrNamedArgument argument) {
      GrArgumentLabel label = argument.getLabel();
      if (label != null) {
        PsiElement pparent = argument.getParent().getParent();
        if (pparent instanceof GrCall && resolvesToDefaultConstructor(((GrCall)pparent))) {
          final GroovyResolveResult resolveResult = label.advancedResolve();
          PsiElement resolved = resolveResult.getElement();
          PsiType type = resolved instanceof PsiField ?
                            ((PsiField)resolved).getType() :
                         resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)resolved) ?
                            ((PsiMethod)resolved).getParameterList().getParameters()[0].getType()
                         : null;

          PsiType substituted = resolveResult.getSubstitutor().substitute(type);
          if (substituted != null) {
            myResult = createSimpleSubTypeResult(substituted);
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

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCall) {
      final GrExpression invokedExpression = methodCall.getInvokedExpression();
      if (myExpression.equals(invokedExpression)) {
        myResult = new TypeConstraint[]{SubtypeConstraint.create(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, methodCall)};
        return;
      }

      final GrClosableBlock[] closureArgs = methodCall.getClosureArguments();
      if (ArrayUtil.contains(myExpression, (Object[])closureArgs)) {
        final GrArgumentList argumentList = methodCall.getArgumentList();
        final GrNamedArgument[] namedArgs = argumentList.getNamedArguments();
        final GrExpression[] expressionArgs = argumentList.getExpressionArguments();
        final GroovyResolveResult[] callVariants = ResolveUtil.getCallVariants(myExpression);
        processCallVariants(methodCall, callVariants, namedArgs, expressionArgs, closureArgs);
      }
    }

    @Override
    public void visitOpenBlock(GrOpenBlock block) {
      final GrStatement[] statements = block.getStatements();
      if (statements.length > 0 && myExpression.equals(statements[statements.length - 1])) {
        checkExitPoint();
      }
    }

    @Override
    public void visitIfStatement(GrIfStatement ifStatement) {
      if (myExpression.equals(ifStatement.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(ifStatement), PsiType.BOOLEAN)};
      }
      else if (myExpression.equals(ifStatement.getThenBranch()) || myExpression.equals(ifStatement.getElseBranch())) {
        checkExitPoint();
      }
    }

    @Override
    public void visitAnnotationMethod(GrAnnotationMethod method) {
      PsiType type = method.getReturnType();
      if (type != null && isAcceptableAnnotationValueType(type)) {
        myResult = createSimpleSubTypeResult(type);
      }
    }

    @Override
    public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
      final GrAnnotationNameValuePair nameValuePair = PsiTreeUtil.getParentOfType(arrayInitializer, GrAnnotationNameValuePair.class, true, GrAnnotationMethod.class);
      if (nameValuePair != null) {

        final PsiClass annot = ResolveUtil.resolveAnnotation(arrayInitializer);
        if (annot == null) return;

        final String name = nameValuePair.getName();
        if (name == null) return;

        createResultFromAttrName(annot, name);
      }
      else {
        final GrAnnotationMethod method = PsiTreeUtil.getParentOfType(arrayInitializer, GrAnnotationMethod.class);
        assert method != null;

        PsiType type = method.getReturnType();

        int count = 1;
        PsiElement parent = arrayInitializer.getParent();
        while (parent instanceof GrAnnotationArrayInitializer) {
          count++;
          parent = parent.getParent();
        }

        while (type instanceof PsiArrayType && count > 0) {
          type = ((PsiArrayType)type).getComponentType();
          count--;
        }
        if (type != null && isAcceptableAnnotationValueType(type)) {
          myResult = createSimpleSubTypeResult(type);
        }
      }
    }

    @Override
    public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
      if (myExpression.equals(nameValuePair.getValue())) {
        final PsiClass annot = ResolveUtil.resolveAnnotation(nameValuePair.getParent());
        if (annot != null) {
          final String name = nameValuePair.getName();
          if (name != null) {
            createResultFromAttrName(annot, name);
          }
          else {
            final PsiMethod[] valueAttr = annot.findMethodsByName("value", false);
            if (valueAttr.length > 0) {
              boolean canHaveSimpleExpr = ContainerUtil.find(annot.getMethods(), method -> !("value".equals(method.getName()) || method instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)method).getDefaultValue() != null)) == null;


              if (canHaveSimpleExpr) {
                createResultFromAnnotationAttribute(valueAttr[0]);
              }
            }
          }
        }
      }
    }

    private void createResultFromAttrName(PsiClass annotation, String attrName) {
      final PsiMethod[] attrs = annotation.findMethodsByName(attrName, false);
      if (attrs.length > 0) {
        createResultFromAnnotationAttribute(attrs[0]);
      }
    }

    private void createResultFromAnnotationAttribute(PsiMethod attr) {
      PsiType type = attr.getReturnType();
      while (type instanceof PsiArrayType) type = ((PsiArrayType)type).getComponentType();
      if (type != null && isAcceptableAnnotationValueType(type)) {
        myResult = createSimpleSubTypeResult(type);
      }
    }

    private static boolean isAcceptableAnnotationValueType(PsiType type) {
      //noinspection ConstantConditions
      return type instanceof PsiPrimitiveType ||
       type.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
       type.equalsToText(CommonClassNames.JAVA_LANG_CLASS) ||
       type instanceof PsiClassType && ((PsiClassType)type).resolve() != null && ((PsiClassType)type).resolve().isEnum();
    }

    @NotNull
    private static TypeConstraint[] createSimpleSubTypeResult(@NotNull PsiType type) {
      return new TypeConstraint[]{new SubtypeConstraint(type, type)};
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
                myResult = createSimpleSubTypeResult(returnType);
              }
              return false;
            }
            return true;
          }
        });
      }
    }

    @Override
    public void visitWhileStatement(GrWhileStatement whileStatement) {
      if (myExpression.equals(whileStatement.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(whileStatement), PsiType.BOOLEAN)};
      }
    }

    @Override
    public void visitTraditionalForClause(GrTraditionalForClause forClause) {
      if (myExpression.equals(forClause.getCondition())) {
        myResult = new TypeConstraint[]{new SubtypeConstraint(TypesUtil.getJavaLangObject(forClause), PsiType.BOOLEAN)};
      }
    }

    @Override
    public void visitArgumentList(GrArgumentList list) {
      processCallVariants(list, ResolveUtil.getCallVariants(list), list.getNamedArguments(), list.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY);
    }

    private void processCallVariants(@NotNull PsiElement place,
                                     @NotNull GroovyResolveResult[] variants,
                                     @NotNull GrNamedArgument[] namedArguments,
                                     @NotNull GrExpression[] expressionArguments,
                                     @NotNull GrClosableBlock[] closureArguments) {

      List<Pair<PsiParameter, PsiType>> expectedParams =
        ResolveUtil.collectExpectedParamsByArg(place, variants, namedArguments, expressionArguments, closureArguments, myExpression);

      collectExpectedTypeFromPossibleParams(expectedParams);
    }

    private void collectExpectedTypeFromPossibleParams(List<Pair<PsiParameter, PsiType>> expectedParams) {
      List<TypeConstraint> constraints = ContainerUtil.newArrayList();
      for (Pair<PsiParameter, PsiType> pair : expectedParams) {
        final PsiType type = pair.second;
        if (type != null) {

          constraints.add(SubtypeConstraint.create(type));

          if (type instanceof PsiArrayType && pair.first.isVarArgs()) {
            constraints.add(SubtypeConstraint.create(((PsiArrayType)type).getComponentType()));
          }
        }
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

      final GrExpression other = myExpression == left ? right : left;
      final PsiType otherType = other != null ? other.getType() : null;

      if (otherType == null) return;

      final GroovyResolveResult[] callVariants = expression.multiResolve(true);
      if (myExpression == left || callVariants.length == 0) {
        if (type == GroovyTokenTypes.mPLUS && otherType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          final PsiClassType obj = TypesUtil.getJavaLangObject(expression);
          myResult = createSimpleSubTypeResult(obj);
        }
        else if (type == GroovyTokenTypes.mREGEX_FIND || type == GroovyTokenTypes.mREGEX_MATCH) {
          final PsiClassType string = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, expression);
          myResult = createSimpleSubTypeResult(string);
        }
        else {
          myResult = createSimpleSubTypeResult(otherType);
        }
      }
      else { //myExpression == right
        processCallVariants(expression, callVariants, GrNamedArgument.EMPTY_ARRAY, new GrExpression[]{myExpression},
                            GrClosableBlock.EMPTY_ARRAY);
      }
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

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression expression) {
      GrExpression rValue = expression.getRValue();
      GrExpression lValue = expression.getLValue();
      if (myExpression.equals(rValue)) {
        PsiType lType = lValue.getNominalType();
        if (lType != null) {
          myResult = new TypeConstraint[]{SubtypeConstraint.create(lType)};
        }
        else if (lValue instanceof GrReferenceExpression) {
          GroovyResolveResult result = ((GrReferenceExpression)lValue).advancedResolve();
          PsiElement resolved = result.getElement();
          if (resolved instanceof GrVariable) {
            PsiType type = ((GrVariable)resolved).getTypeGroovy();
            if (type != null) {
              myResult = new TypeConstraint[]{SubtypeConstraint.create(result.getSubstitutor().substitute(type))};
            }
          }
        }
      }
      else if (myExpression.equals(lValue)) {
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
        public boolean satisfied(PsiType type, @NotNull PsiElement context) {
          final PsiType boxed = TypesUtil.boxPrimitiveType(type, context.getManager(), context.getResolveScope());
          final IElementType opToken = expression.getOperationTokenType();
          final GrExpression operand = expression.getOperand();
          final GroovyResolveResult[] candidates =
            TypesUtil.getOverloadedUnaryOperatorCandidates(boxed, opToken, operand != null ? operand : expression, PsiType.EMPTY_ARRAY);
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
      final TypeConstraint[] constraints = calculateTypeConstraints(listOrMap);
      List<PsiType> result= new ArrayList<>(constraints.length);
      for (TypeConstraint constraint : constraints) {
        if (constraint instanceof SubtypeConstraint) {
          final PsiType type = constraint.getType();
          final PsiType iterable = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, true);
          if (iterable != null) {
            result.add(iterable);
          }
        }
      }
      if (result.isEmpty()) {
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
      if (!(parent instanceof GrSwitchStatement)) return;

      final GrExpression condition = ((GrSwitchStatement)parent).getCondition();
      if (condition == null) return;

      final PsiType type = condition.getType();
      if (type == null) return;

      myResult = new TypeConstraint[]{SubtypeConstraint.create(type)};
    }

    @Override
    public void visitSwitchStatement(GrSwitchStatement switchStatement) {
      final GrCaseSection[] sections = switchStatement.getCaseSections();
      List<PsiType> types = new ArrayList<>(sections.length);
      for (GrCaseSection section : sections) {
        for (GrCaseLabel label : section.getCaseLabels()) {
          final GrExpression value = label.getValue();
          if (value != null) {
            final PsiType type = value.getType();
            if (type != null) {
              types.add(type);
            }
          }
        }
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
