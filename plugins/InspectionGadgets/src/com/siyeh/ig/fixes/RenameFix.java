/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.*;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.usageView.UsageViewUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenameFix extends RefactoringInspectionGadgetsFix {

  private final String m_targetName;
  private boolean m_searchInStrings = true;
  private boolean m_searchInNonJavaFiles = true;

  public RenameFix() {
    m_targetName = null;
  }

  public RenameFix(@NonNls String targetName) {
    m_targetName = targetName;
  }


  public RenameFix(@NonNls String targetName, boolean searchInStrings, boolean searchInNonJavaFiles) {
    m_targetName = targetName;
    m_searchInStrings = searchInStrings;
    m_searchInNonJavaFiles = searchInNonJavaFiles;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("rename.quickfix");
  }

  @Override
  @NotNull
  public String getName() {
    if (m_targetName == null) {
      return InspectionGadgetsBundle.message("rename.quickfix");
    }
    else {
      return InspectionGadgetsBundle.message("renameto.quickfix", m_targetName);
    }
  }

  public String getTargetName() {
    return m_targetName;
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return RefactoringActionHandlerFactory.getInstance().createRenameHandler();
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler(DataContext context) {
    RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
    return renameHandler != null ? renameHandler : getHandler();
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (m_targetName == null) {
      super.doFix(project, descriptor);
    }
    else {
      final PsiElement nameIdentifier = descriptor.getPsiElement();
      final PsiElement elementToRename = nameIdentifier.getParent();
      final RefactoringFactory factory = RefactoringFactory.getInstance(project);
      final RenameRefactoring renameRefactoring =
        factory.createRename(elementToRename, m_targetName, m_searchInStrings, m_searchInNonJavaFiles);
      renameRefactoring.run();
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiElement element = getElementToRefactor(previewDescriptor.getPsiElement());
    if (element instanceof PsiNamedElement namedElement) {
      if (m_targetName == null) {
        String what = UsageViewUtil.getType(element) + " '" + namedElement.getName() + "'";
        String message = RefactoringBundle.message("rename.0.and.its.usages.preview.text", what);
        return new IntentionPreviewInfo.Html(HtmlChunk.text(message));
      }
      ((PsiNamedElement)element).setName(m_targetName);
      return IntentionPreviewInfo.DIFF;
    }
    return IntentionPreviewInfo.EMPTY;
  }
}