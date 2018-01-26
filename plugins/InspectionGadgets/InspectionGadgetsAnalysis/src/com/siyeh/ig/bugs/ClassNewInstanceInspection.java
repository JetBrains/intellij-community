/*
 * Copyright 2009-2018 Bas Leijdekkers
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ClassNewInstanceInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.new.instance.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.new.instance.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ClassNewInstanceFix();
  }

  private static class ClassNewInstanceFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "class.new.instance.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiElement parentOfType = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiTryStatement.class, PsiLambdaExpression.class);
      if (parentOfType instanceof PsiTryStatement) {
        final PsiTryStatement tryStatement = (PsiTryStatement)parentOfType;
        addCatchBlock(tryStatement, "java.lang.NoSuchMethodException", "java.lang.reflect.InvocationTargetException");
      }
      else if (parentOfType instanceof PsiLambdaExpression) {
        final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(parentOfType);
        if (FileModificationService.getInstance().preparePsiElementsForWrite(method)) {
          addThrowsClause(method, "java.lang.NoSuchMethodException",
                          "java.lang.reflect.InvocationTargetException");
        }
      }
      else {
        final PsiMethod method = (PsiMethod)parentOfType;
        addThrowsClause(method, "java.lang.NoSuchMethodException", "java.lang.reflect.InvocationTargetException");
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      @NonNls final String newExpression = qualifier.getText() + ".getConstructor().newInstance()";
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression, new CommentTracker());
    }

    private static void addThrowsClause(PsiMethod method,
                                        String... exceptionNames) {
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
      final Set<String> presentExceptionNames = new HashSet<>();
      for (PsiClassType referencedType : referencedTypes) {
        final String exceptionName = referencedType.getCanonicalText();
        presentExceptionNames.add(exceptionName);
      }
      final Project project = method.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final GlobalSearchScope scope = method.getResolveScope();
      for (String exceptionName : exceptionNames) {
        if (presentExceptionNames.contains(exceptionName)) {
          continue;
        }
        final PsiJavaCodeReferenceElement throwsReference = factory.createReferenceElementByFQClassName(exceptionName, scope);
        final PsiElement element = throwsList.add(throwsReference);
        codeStyleManager.shortenClassReferences(element);
      }
    }

    protected static void addCatchBlock(PsiTryStatement tryStatement, String... exceptionNames) {
      final Project project = tryStatement.getProject();
      final PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
      final Set<String> presentExceptionNames = new HashSet<>();
      for (PsiParameter parameter : parameters) {
        final PsiType type = parameter.getType();
        final String exceptionName = type.getCanonicalText();
        presentExceptionNames.add(exceptionName);
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String name = codeStyleManager.suggestUniqueVariableName("e",
                                                                     tryStatement.getTryBlock(), false);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (String exceptionName : exceptionNames) {
        if (presentExceptionNames.contains(exceptionName)) {
          continue;
        }
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(exceptionName, tryStatement);
        final PsiCatchSection section = factory.createCatchSection(type, name, tryStatement);
        final PsiCatchSection element = (PsiCatchSection)tryStatement.add(section);
        codeStyleManager.shortenClassReferences(element);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNewInstanceVisitor();
  }

  private static class ClassNewInstanceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"newInstance".equals(methodName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!CommonClassNames.JAVA_LANG_CLASS.equals(TypeUtils.resolvedClassName(qualifier.getType()))) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}