/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class StringBufferReplaceableByStringBuilderInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getID() {
    return "StringBufferMayBeStringBuilder";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new StringBufferMayBeStringBuilderFix();
  }

  @Nullable
  private static PsiNewExpression getNewStringBuffer(PsiExpression expression) {
    if (expression == null) {
      return null;
    }
    else if (expression instanceof PsiNewExpression) {
      return (PsiNewExpression)expression;
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"append".equals(methodName)) {
        return null;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return getNewStringBuffer(qualifier);
    }
    return null;
  }

  private static class StringBufferMayBeStringBuilderFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass stringBuilderClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING_BUILDER, element.getResolveScope());
      if (stringBuilderClass == null) {
        return;
      }
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiJavaCodeReferenceElement stringBuilderClassReference = factory.createClassReferenceElement(stringBuilderClass);
      final PsiClassType stringBuilderType = factory.createType(stringBuilderClass);
      final PsiTypeElement stringBuilderTypeElement = factory.createTypeElement(stringBuilderType);
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiDeclarationStatement)) {
        return;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)grandParent;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiVariable)) {
          continue;
        }
        replaceWithStringBuilder(stringBuilderClassReference, stringBuilderTypeElement, (PsiVariable)declaredElement);
      }
    }

    private static void replaceWithStringBuilder(PsiJavaCodeReferenceElement newClassReference,
                                                 PsiTypeElement newTypeElement,
                                                 PsiVariable variable) {
      final PsiNewExpression newExpression = getNewStringBuffer(variable.getInitializer());
      if (newExpression == null) {
        return;
      }
      final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference(); // no anonymous classes because StringBuffer is final
      if (classReference == null) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.getParent() == variable) {
        typeElement.replace(newTypeElement);
      }
      classReference.replace(newClassReference);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringBuilderVisitor();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  private static class StringBufferReplaceableByStringBuilderVisitor extends BaseInspectionVisitor {

    private static final Set<String> excludes = ContainerUtil.newHashSet(CommonClassNames.JAVA_LANG_STRING_BUILDER,
                                                                         CommonClassNames.JAVA_LANG_STRING_BUFFER);

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          return;
        }
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
        final PsiElement context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
        if (!isReplaceableStringBuffer(variable, context)) {
          return;
        }
      }
      final PsiLocalVariable firstVariable = (PsiLocalVariable)declaredElements[0];
      registerVariableError(firstVariable);
    }

    private static boolean isReplaceableStringBuffer(PsiVariable variable, PsiElement context) {
      if (context == null) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type)) {
        return false;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return false;
      }
      if (getNewStringBuffer(initializer) == null) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsReturned(variable, context, true)) {
        return false;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context, true, call -> {
        final PsiMethod method = call.resolveMethod();
        if (method == null) {
          return false;
        }
        final PsiClass aClass = method.getContainingClass();
        return aClass != null && excludes.contains(aClass.getQualifiedName());
      })) {
        return false;
      }
      return true;
    }
  }
}