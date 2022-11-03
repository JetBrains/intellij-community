// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Junit4ConverterInspection extends BaseInspection {

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    if (PsiUtil.isLanguageLevel5OrHigher(file)) return true;
    return super.shouldInspect(file);
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("convert.junit3.test.case.error.string");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        super.visitClass(aClass);
        if (possibleToConvert(aClass)) {
          registerClassError(aClass);
        }
      }

      private boolean possibleToConvert(PsiClass aClass) {
        final PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList == null) return false;

        final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
        if (referenceElements.length != 1) return false;

        final PsiJavaCodeReferenceElement referenceElement = referenceElements[0];
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass)) return false;

        final PsiClass targetClass = (PsiClass)target;
        final String name = targetClass.getQualifiedName();
        if (!"junit.framework.TestCase".equals(name)) return false;

        final Project project = aClass.getProject();
        final GlobalSearchScope scope = aClass.getResolveScope();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass testAnnotation = psiFacade.findClass("org.junit.Test", scope);
        return testAnnotation != null;
      }
    };
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass pClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
        JUnit4AnnotatedMethodInJUnit3TestCaseInspection.convertJUnit3ClassToJUnit4(pClass);
      }

      @Override
      public @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("convert.junit3.test.case.family.name");
      }
    };
  }
}
