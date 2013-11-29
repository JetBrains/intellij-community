/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
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

  private static final Logger LOG = Logger.getInstance(GroovyExpectedTypesProvider.class);

  public static TypeConstraint[] calculateTypeConstraints(@NotNull final GrExpression expression) {
    return TypeInferenceHelper.getCurrentContext().getCachedValue(expression, new Computable<TypeConstraint[]>() {
      @Override
      public TypeConstraint[] compute() {
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
            return custom.toArray(new TypeConstraint[custom.size()]);
          }

          return result;
        }
      });
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
          PsiType type;
          if (resolved instanceof PsiField) {
            type = ((PsiField)resolved).getType();
          }
          else if (resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)resolved)) {
            type = ((PsiMethod)resolved).getParameterList().getParameters()[0].getType();
          }
          else {
            type = null;
          }
          type = resolveResult.getSubstitutor().substitute(type);
          if (type != null) {
            myResult = createSimpleSubTypeResult(type);
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
          try {
            final Map<GrExpression, Pair<PsiParameter, PsiType>> map =
              GrClosureSignatureUtil.mapArgumentsToParameters(variant, methodCall, true, false, namedArgs, expressionArgs, closureArgs);
            addConstraintsFromMap(constraints, map);
          }
          catch (RuntimeException e) {
            LOG.error("call: " + methodCall.getText() + "\nsymbol: " + variant.getElement().getText(), e);
          }
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

    @Override
    public void visitDefaultAnnotationValue(GrDefaultAnnotationValue defaultAnnotationValue) {
      final GrAnnotationMethod method = ((GrAnnotationMethod)defaultAnnotationValue.getParent());

      PsiType type = method.getReturnType();
      if (type != null && isAcceptableAnnotationValueType(type)) {
        myResult = createSimpleSubTypeResult(type);
      }
    }

    @Override
    public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
      final GrAnnotationNameValuePair nameValuePair = PsiTreeUtil.getParentOfType(arrayInitializer, GrAnnotationNameValuePair.class, true, GrDefaultAnnotationValue.class);
      if (nameValuePair != null) {

        final PsiClass annot = ResolveUtil.resolveAnnotation(arrayInitializer);
        if (annot == null) return;

        final String name = nameValuePair.getName();
        if (name == null) return;

        final PsiMethod[] attrs = annot.findMethodsByName(name, false);
        if (attrs.length > 0) {
          PsiType type = attrs[0].getReturnType();
          while (type instanceof PsiArrayType) type = ((PsiArrayType)type).getComponentType();
          if (type != null && isAcceptableAnnotationValueType(type)) {
            myResult = createSimpleSubTypeResult(type);
          }
        }
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
            final PsiMethod[] attrs = annot.findMethodsByName(name, false);
            if (attrs.length > 0) {
              PsiType type = attrs[0].getReturnType();
              while (type instanceof PsiArrayType) type = ((PsiArrayType)type).getComponentType();
              if (type != null && isAcceptableAnnotationValueType(type)) {
                myResult = createSimpleSubTypeResult(type);
              }
            }
          }
          else {
            final PsiMethod[] valueAttr = annot.findMethodsByName("value", false);
            boolean canHaveSimpleExpr = valueAttr.length > 0;
            final PsiMethod[] methods = annot.getMethods();
            for (PsiMethod method : methods) {
              if (!("value".equals(method.getName()) || method instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)method).getDefaultValue() != null)) {
                canHaveSimpleExpr = false;
              }
            }

            if (canHaveSimpleExpr) {
              PsiType type = valueAttr[0].getReturnType();
              while (type instanceof PsiArrayType) type = ((PsiArrayType)type).getComponentType();
              if (type != null && isAcceptableAnnotationValueType(type)) {
                myResult = createSimpleSubTypeResult(type);
              }
            }
          }
        }
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
        final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil.mapArgumentsToParameters(
          variant, list, true, true, list.getNamedArguments(), list.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY
        );
        addConstraintsFromMap(constraints, map);
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
        myResult = createSimpleSubTypeResult(string);
        return;
      }

      final GrExpression other = myExpression == left ? right : left;
      final PsiType otherType = other != null ? other.getType() : null;

      if (otherType == null) return;

      if (type== mPLUS && otherType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        final PsiClassType obj = TypesUtil.getJavaLangObject(expression);
        myResult = createSimpleSubTypeResult(obj);
        return;
      }

      myResult = createSimpleSubTypeResult(otherType);
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

    private void addConstraintsFromMap(List<TypeConstraint> constraints, Map<GrExpression, Pair<PsiParameter, PsiType>> map) {
      if (map == null) return;

      final Pair<PsiParameter, PsiType> pair = map.get(myExpression);
      if (pair == null) return;

      final PsiType type = pair.second;
      if (type == null) return;

      constraints.add(SubtypeConstraint.create(type));

      if (type instanceof PsiArrayType && pair.first.isVarArgs()) {
        constraints.add(SubtypeConstraint.create(((PsiArrayType)type).getComponentType()));
      }
    }

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
