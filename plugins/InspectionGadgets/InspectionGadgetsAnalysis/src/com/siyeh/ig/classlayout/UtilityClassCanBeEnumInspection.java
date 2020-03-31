// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class UtilityClassCanBeEnumInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.code.can.be.enum.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UtilityClassCanBeEnumFix();
  }

  private static class UtilityClassCanBeEnumFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.code.can.be.enum.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!PsiUtil.isLanguageLevel5OrHigher(element)) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      for (PsiMethod constructor : aClass.getConstructors()) {
        constructor.delete();
      }
      final List<PsiKeyword> keywords = PsiTreeUtil.getChildrenOfTypeAsList(aClass, PsiKeyword.class);
      if (keywords.isEmpty()) {
        return;
      }
      final PsiModifierList modifierList = aClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.STATIC, false); // remove redundant modifier because nested enum is implicitly static
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiStatement statement = factory.createStatementFromText(";", element);
      final PsiElement token = statement.getChildren()[0];
      aClass.addAfter(token, aClass.getLBrace());
      final PsiKeyword newKeyword = factory.createKeyword(PsiKeyword.ENUM);
      keywords.get(0).replace(newKeyword);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UtilityClassCanBeEnumVisitor();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  private static class UtilityClassCanBeEnumVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isEnum()) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass) || !UtilityClassUtil.hasPrivateEmptyOrNoConstructor(aClass)) {
        return;
      }
      for (PsiReference reference : ReferencesSearch.search(aClass)) {
        if (reference.getElement().getParent() instanceof PsiNewExpression) {
          return;
        }
      }
      registerClassError(aClass);
    }
  }
}
