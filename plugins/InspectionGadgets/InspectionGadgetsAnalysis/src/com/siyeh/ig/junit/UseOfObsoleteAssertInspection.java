/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UseOfObsoleteAssertInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("usage.of.obsolete.assert.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("use.of.obsolete.assert.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceObsoleteAssertsFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfObsoleteAssertVisitor();
  }

  private static class UseOfObsoleteAssertVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final Project project = expression.getProject();
      final Module module = ModuleUtilCore.findModuleForPsiElement(expression);
      if (module == null) {
        return;
      }
      final PsiClass newAssertClass = JavaPsiFacade.getInstance(project)
        .findClass("org.junit.Assert", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (newAssertClass == null) {
        return;
      }
      final PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod == null || !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null && Comparing.strEqual(containingClass.getQualifiedName(), "junit.framework.Assert")) {
        registerMethodCallError(expression);
      }
    }
  }

  private static class ReplaceObsoleteAssertsFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (psiElement == null) {
        return;
      }
      final PsiClass newAssertClass =
        JavaPsiFacade.getInstance(project).findClass("org.junit.Assert", GlobalSearchScope.allScope(project));
      final PsiClass oldAssertClass =
        JavaPsiFacade.getInstance(project).findClass("junit.framework.Assert", GlobalSearchScope.allScope(project));

      if (newAssertClass == null) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)psiElement;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      final PsiElement usedImport = qualifierExpression instanceof PsiReferenceExpression ?
                                    ((PsiReferenceExpression)qualifierExpression).advancedResolve(true).getCurrentFileResolveScope() :
                                    methodExpression.advancedResolve(true).getCurrentFileResolveScope();
      final PsiMethod psiMethod = methodCallExpression.resolveMethod();

      final boolean isImportUnused = isImportBecomeUnused(methodCallExpression, usedImport, psiMethod);

      PsiImportStaticStatement staticStatement = null;
      if (qualifierExpression == null) {
        staticStatement = staticallyImported(oldAssertClass, methodExpression);
      }

      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      if (staticStatement == null) {
        methodExpression.setQualifierExpression(JavaPsiFacade.getElementFactory(project).createReferenceExpression(newAssertClass));

        if (isImportUnused && usedImport instanceof PsiImportStatementBase) {
          usedImport.delete();
        }

        styleManager.shortenClassReferences(methodExpression);
      }
      else {
        if (isImportUnused) {
          final PsiJavaCodeReferenceElement importReference = staticStatement.getImportReference();
          if (importReference != null) {
            if (staticStatement.isOnDemand()) {
              importReference.bindToElement(newAssertClass);
            }
            else {
              final PsiElement importQExpression = importReference.getQualifier();
              if (importQExpression instanceof PsiJavaCodeReferenceElement) {
                ((PsiJavaCodeReferenceElement)importQExpression).bindToElement(newAssertClass);
              }
            }
          }
        }
        else {
          methodExpression
            .setQualifierExpression(JavaPsiFacade.getElementFactory(project).createReferenceExpression(newAssertClass));
          styleManager.shortenClassReferences(methodExpression);
        }
      }
      /*
          //refs can be optimized now but should we really?
          if (isImportUnused) {
            for (PsiReference reference : ReferencesSearch.search(newAssertClass, new LocalSearchScope(methodCallExpression.getContainingFile()))) {
              final PsiElement element = reference.getElement();
              styleManager.shortenClassReferences(element);
            }
          }*/
    }

    private static boolean isImportBecomeUnused(final PsiMethodCallExpression methodCallExpression,
                                                final PsiElement usedImport,
                                                final PsiMethod psiMethod) {
      final boolean[] proceed = new boolean[]{true};
      methodCallExpression.getContainingFile().accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (proceed[0]) {
            super.visitElement(element);
          }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (expression == methodCallExpression) {
            return;
          }
          final PsiMethod resolved = expression.resolveMethod();
          if (resolved == psiMethod) {
            proceed[0] = false;
          }
          else {
            final PsiElement resolveScope =
              expression.getMethodExpression().advancedResolve(false).getCurrentFileResolveScope();
            if (resolveScope == usedImport) {
              proceed[0] = false;
            }
          }
        }
      });
      return proceed[0];
    }

    @Nullable
    private static PsiImportStaticStatement staticallyImported(PsiClass oldAssertClass, PsiReferenceExpression methodExpression) {
      final String referenceName = methodExpression.getReferenceName();
      final PsiFile containingFile = methodExpression.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return null;
      }
      final PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList == null) {
        return null;
      }
      final PsiImportStaticStatement[] statements = importList.getImportStaticStatements();
      for (PsiImportStaticStatement statement : statements) {
        if (oldAssertClass != statement.resolveTargetClass()) {
          continue;
        }
        final String importRefName = statement.getReferenceName();
        final PsiJavaCodeReferenceElement importReference = statement.getImportReference();
        if (importReference == null) {
          continue;
        }
        if (Comparing.strEqual(importRefName, referenceName)) {
          final PsiElement qualifier = importReference.getQualifier();
          if (qualifier instanceof PsiJavaCodeReferenceElement) {
            return statement;
          }
        }
        else if (importRefName == null) {
          return statement;
        }
      }
      return null;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("use.of.obsolete.assert.quickfix");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

  }
}