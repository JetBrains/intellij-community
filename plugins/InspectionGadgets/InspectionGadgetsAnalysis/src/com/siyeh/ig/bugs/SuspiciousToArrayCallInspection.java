/*
 * Copyright 2005-2019 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.FunctionalExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuspiciousToArrayCallInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final PsiType foundType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor", type.getCanonicalText(), foundType.getCanonicalText());
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SuspiciousToArrayCallFix((PsiType)infos[0], (boolean)infos[2]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousToArrayCallVisitor();
  }

  private static class SuspiciousToArrayCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"toArray".equals(methodName)) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      final PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (argument == null) return;

      final PsiClassType classType = (PsiClassType)type;
      if (classType.isRaw()) return;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(classType, expression.getResolveScope());
        PsiType argumentType = argument.getType();
        if (!(argumentType instanceof PsiArrayType)) {
          argumentType = getIntFunctionParameterType(argument);
        }
        checkArrayTypes(argument, expression, argumentType, itemType);
      }
      else if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
        PsiType argumentType = getIntFunctionParameterType(argument);
        if (argumentType != null) {
          checkArrayTypes(argument, expression, argumentType, StreamApiUtil.getStreamElementType(classType, false));
        }
      }
    }

    private static PsiType getIntFunctionParameterType(PsiExpression argument) {
      PsiType argumentType = FunctionalExpressionUtils.getFunctionalExpressionType(argument);
      return PsiUtil.substituteTypeParameter(argumentType, "java.util.function.IntFunction", 0, false);
    }

    private void checkArrayTypes(@NotNull PsiExpression argument,
                                 @NotNull PsiMethodCallExpression expression,
                                 PsiType argumentType,
                                 PsiType itemType) {
      if (!(argumentType instanceof PsiArrayType)) {
        return;
      }
      final PsiArrayType arrayType = (PsiArrayType)argumentType;
      final PsiType componentType = arrayType.getComponentType();
      PsiType actualType = getActualItemTypeIfMismatch(arrayType, expression, itemType);
      if (actualType != null) {
        registerError(argument, actualType, componentType, !(argument.getType() instanceof PsiArrayType));
      }
    }

    @Nullable
    private static PsiType getActualItemTypeIfMismatch(@NotNull PsiArrayType arrayType,
                                                       @NotNull PsiMethodCallExpression expression,
                                                       PsiType itemType) {
      itemType = GenericsUtil.getVariableTypeByExpressionType(itemType);
      final PsiType componentType = arrayType.getComponentType();
      if (itemType == null || componentType.isAssignableFrom(itemType)) return null;
      if (itemType instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)itemType).resolve();
        if (aClass instanceof PsiTypeParameter) {
          final PsiReferenceList extendsList = ((PsiTypeParameter)aClass).getExtendsList();
          final PsiClassType[] types = extendsList.getReferencedTypes();
          if (types.length == 0) {
            return TypeUtils.getObjectType(expression);
          }
          if (types.length == 1) {
            return types[0];
          }
          return null;
        }
      }
      return itemType;
    }
  }

  private static class SuspiciousToArrayCallFix extends InspectionGadgetsFix {
    @NonNls private final String myReplacement;
    @NonNls private final String myPresented;
    
    SuspiciousToArrayCallFix(PsiType wantedType, boolean isFunction) {
      if (isFunction) {
        myReplacement = wantedType.getCanonicalText() + "[]::new";
        myPresented = wantedType.getPresentableText() + "[]::new";
      } else {
        final String index = StringUtil.repeat("[0]", wantedType.getArrayDimensions() + 1);
        final PsiType componentType = wantedType.getDeepComponentType();
        myReplacement = "new " + componentType.getCanonicalText() + index;
        myPresented = "new " + componentType.getPresentableText() + index;
      }
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiExpression expression = ObjectUtils.tryCast(descriptor.getStartElement(), PsiExpression.class);
      if (expression == null) return;
      new CommentTracker().replaceAndRestoreComments(expression, myReplacement);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myPresented);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("suspicious.to.array.call.fix.family.name");
    }
  }
}