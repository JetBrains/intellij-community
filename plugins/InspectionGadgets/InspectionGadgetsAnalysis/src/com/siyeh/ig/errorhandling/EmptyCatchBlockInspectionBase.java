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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EmptyCatchBlockInspectionBase extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_includeComments = true;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreTestCases = true; // keep for compatibility
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreIgnoreParameter = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("empty.catch.block.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("empty.catch.block.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("empty.catch.block.comments.option"), "m_includeComments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("empty.catch.block.ignore.ignore.option"), "m_ignoreIgnoreParameter");
    return optionsPanel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EmptyCatchBlockFix();
  }

  private static class EmptyCatchBlockFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("rename.catch.parameter.to.ignored");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiCatchSection)) {
        return;
      }
      final PsiCatchSection catchSection = (PsiCatchSection)parent;
      final PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) {
        return;
      }
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiIdentifier newIdentifier = factory.createIdentifier("ignored");
      identifier.replace(newIdentifier);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyCatchBlockVisitor();
  }

  private class EmptyCatchBlockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (final PsiCatchSection section : catchSections) {
        checkCatchSection(section);
      }
    }

    private void checkCatchSection(PsiCatchSection section) {
      final PsiCodeBlock block = section.getCatchBlock();
      if (block == null || !isEmpty(block)) {
        return;
      }
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      @NonNls final String parameterName = parameter.getName();
      if (m_ignoreIgnoreParameter && PsiUtil.isIgnoredName(parameterName)) {
        return;
      }
      final PsiElement catchToken = section.getFirstChild();
      if (catchToken == null) {
        return;
      }
      registerError(catchToken, catchToken);
    }

    private boolean isEmpty(PsiElement element) {
      if (!m_includeComments && element instanceof PsiComment) {
        return true;
      }
      else if (element instanceof PsiEmptyStatement) {
        return !m_includeComments || PsiTreeUtil.getChildOfType(element, PsiComment.class) == null;
      }
      else if (element instanceof PsiWhiteSpace) {
        return true;
      }
      else if (element instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)element;
        return isEmpty(block.getCodeBlock());
      }
      else if (element instanceof PsiCodeBlock) {
        final PsiCodeBlock codeBlock = (PsiCodeBlock)element;
        PsiElement bodyElement = codeBlock.getFirstBodyElement();
        final PsiElement lastBodyElement = codeBlock.getLastBodyElement();
        while (bodyElement != null) {
          if (!isEmpty(bodyElement)) {
            return false;
          }
          if (bodyElement == lastBodyElement) {
            break;
          }
          bodyElement = bodyElement.getNextSibling();
        }
        return true;
      }
      return false;
    }
  }
}