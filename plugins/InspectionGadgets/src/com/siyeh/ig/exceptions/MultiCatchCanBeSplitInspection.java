/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.exceptions;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class MultiCatchCanBeSplitInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("multi.catch.can.be.split.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SplitMultiCatchVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SplitMultiCatchFix();
  }

  private static void doFixImpl(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return;
    }
    final PsiCatchSection catchSection = (PsiCatchSection)parent;
    final PsiElement grandParent = catchSection.getParent();
    if (!(grandParent instanceof PsiTryStatement)) {
      return;
    }
    final PsiParameter parameter = catchSection.getParameter();
    if (parameter == null) {
      return;
    }
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiDisjunctionType)) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(element.getProject());
    for (PsiType disjunction : ((PsiDisjunctionType)type).getDisjunctions()) {
      final PsiCatchSection copy = (PsiCatchSection)catchSection.copy();

      final PsiTypeElement typeElement = assertNotNull(assertNotNull(copy.getParameter()).getTypeElement());
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(disjunction.getCanonicalText(true), catchSection);
      final PsiElement replaced = typeElement.replace(newTypeElement);

      grandParent.addBefore(copy, catchSection);
      styleManager.shortenClassReferences(replaced);
    }

    catchSection.delete();
  }

  private static boolean isAcceptable(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiCatchSection) {
      final PsiType type = ((PsiCatchSection)parent).getCatchType();
      return  type instanceof PsiDisjunctionType;
    }
    return false;
  }

  private static class SplitMultiCatchVisitor extends BaseInspectionVisitor {
    @Override
    public void visitParameter(PsiParameter parameter) {
      super.visitParameter(parameter);
      if (isAcceptable(parameter)) {
        registerError(parameter);
      }
    }

    @Override
    public void visitKeyword(PsiKeyword keyword) {
      super.visitKeyword(keyword);
      if (isOnTheFly() && keyword.getTokenType() == JavaTokenType.CATCH_KEYWORD && isAcceptable(keyword)) {
        registerError(keyword);
      }
    }
  }

  private static class SplitMultiCatchFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("multi.catch.can.be.split.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      doFixImpl(descriptor.getPsiElement());
    }
  }
}
