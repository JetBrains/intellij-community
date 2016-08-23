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
package com.siyeh.ig.exceptions;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TryStatementWithMultipleResourcesInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.statement.with.multiple.resources.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SplitTryWithResourcesVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SplitTryWithResourcesFix();
  }

  private static void doFixImpl(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null || resourceList.getResourceVariablesCount() <= 1) {
      return;
    }
    final StringBuilder newTryStatementText = new StringBuilder();
    int count = 0;
    for (PsiResourceListElement resource : resourceList) {
      if (count > 0) {
        newTryStatementText.append("{\n");
      }
      ++count;
      newTryStatementText.append("try (").append(resource.getText()).append(")");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    newTryStatementText.append(tryBlock.getText());
    for (int i = 1; i < count; i++) {
      newTryStatementText.append("\n}");
    }
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      newTryStatementText.append(catchSection.getText());
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      newTryStatementText.append("finally").append(finallyBlock.getText());
    }
    PsiReplacementUtil.replaceStatement(tryStatement, newTryStatementText.toString());
  }

  private static boolean isAcceptable(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiTryStatement)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return false;
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    return resourceList.getResourceVariablesCount() > 1;
  }

  private static class SplitTryWithResourcesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitKeyword(PsiKeyword keyword) {
      super.visitKeyword(keyword);
      if (isOnTheFly() && keyword.getTokenType() == JavaTokenType.TRY_KEYWORD && isAcceptable(keyword)) {
        registerError(keyword);
      }
    }

    @Override
    public void visitResourceList(PsiResourceList resourceList) {
      super.visitResourceList(resourceList);
      if (isAcceptable(resourceList)) {
        registerError(resourceList);
      }
    }
  }

  private static class SplitTryWithResourcesFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      doFixImpl(descriptor.getPsiElement());
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("try.statement.with.multiple.resources.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }
  }
}
