/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ObsoleteCollectionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreRequiredObsoleteCollectionTypes = false;

  @Override
  @NotNull
  public String getID() {
    return "UseOfObsoleteCollectionType";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "use.obsolete.collection.type.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.obsolete.collection.type.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "use.obsolete.collection.type.ignore.library.arguments.option"
    ), this, "ignoreRequiredObsoleteCollectionTypes");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObsoleteCollectionVisitor();
  }

  private class ObsoleteCollectionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final PsiType type = variable.getType();
      if (!isObsoleteCollectionType(type)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes &&
          isUsedAsParameterForLibraryMethod(variable)) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (!isObsoleteCollectionType(returnType)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes &&
          isUsedAsParameterForLibraryMethod(method)) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitNewExpression(
      @NotNull PsiNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      final PsiType type = newExpression.getType();
      if (!isObsoleteCollectionType(type)) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes &&
          isRequiredObsoleteCollectionElement(newExpression)) {
        return;
      }
      registerNewExpressionError(newExpression);
    }

    private boolean isObsoleteCollectionType(@Nullable PsiType type) {
      if (type == null) {
        return false;
      }
      final PsiType deepComponentType = type.getDeepComponentType();
      if (!(deepComponentType instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)deepComponentType;
      @NonNls final String className = classType.getClassName();
      if (!"Vector".equals(className) && !"Hashtable".equals(className)) {
        return false;
      }
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return false;
      }
      final String name = aClass.getQualifiedName();
      return "java.util.Vector".equals(name) ||
             "java.util.Hashtable".equals(name);
    }

    private boolean isUsedAsParameterForLibraryMethod(
      PsiNamedElement namedElement) {
      final PsiFile containingFile = namedElement.getContainingFile();
      final Query<PsiReference> query =
        ReferencesSearch.search(namedElement,
                                GlobalSearchScope.fileScope(containingFile));
      for (PsiReference reference : query) {
        final PsiElement element = reference.getElement();
        if (isRequiredObsoleteCollectionElement(element)) {
          return true;
        }
      }
      return false;
    }

    private boolean isRequiredObsoleteCollectionElement(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        final PsiType variableType = variable.getType();
        if (isObsoleteCollectionType(variableType)) {
          return true;
        }
      }
      else if (parent instanceof PsiReturnStatement) {
        final PsiElement container = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
        final PsiType returnType;
        if (container instanceof PsiMethod) {
          returnType = ((PsiMethod)container).getReturnType();
        } 
        else if (container instanceof PsiLambdaExpression) {
          returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)container);
        }
        else {
          returnType = null;
        }
        if (isObsoleteCollectionType(returnType)) {
          return true;
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final PsiType lhsType = lhs.getType();
        if (isObsoleteCollectionType(lhsType)) {
          return true;
        }
      }
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiExpressionList argumentList = (PsiExpressionList)parent;
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return false;
      }
      final PsiCallExpression callExpression =
        (PsiCallExpression)grandParent;
      final int index = getIndexOfArgument(argumentList, element);
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiParameterList parameterList =
        method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (index >= parameters.length) {
        final PsiParameter lastParameter =
          parameters[parameters.length - 1];
        if (!lastParameter.isVarArgs()) {
          return false;
        }
        final PsiType type = lastParameter.getType();
        if (!(type instanceof PsiEllipsisType)) {
          return false;
        }
        final PsiEllipsisType ellipsisType = (PsiEllipsisType)type;
        final PsiType componentType = ellipsisType.getComponentType();
        return isObsoleteCollectionType(componentType);
      }
      final PsiParameter parameter = parameters[index];
      final PsiType type = parameter.getType();
      return isObsoleteCollectionType(type);
    }

    private int getIndexOfArgument(PsiExpressionList argumentList,
                                   PsiElement argument) {
      final PsiExpression[] expressions =
        argumentList.getExpressions();
      int index = -1;
      for (PsiExpression expression : expressions) {
        index++;
        if (expression.equals(argument)) {
          break;
        }
      }
      return index;
    }
  }
}