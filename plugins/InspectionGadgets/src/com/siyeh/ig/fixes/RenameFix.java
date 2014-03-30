/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.util.Consumer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenameFix extends InspectionGadgetsFix {

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

  @Override
  public void doFix(final Project project, final ProblemDescriptor descriptor) {
    final PsiElement nameIdentifier = descriptor.getPsiElement();
    final PsiElement elementToRename = nameIdentifier.getParent();
    if (m_targetName == null) {
      final AsyncResult<DataContext> contextFromFocus = DataManager.getInstance().getDataContextFromFocus();
      contextFromFocus.doWhenDone(new Consumer<DataContext>() {
        @Override
        public void consume(DataContext context) {
          final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
          if (renameHandler == null) {
            return;
          }
          renameHandler.invoke(project, new PsiElement[]{elementToRename}, context);
        }
      });
    }
    else {
      final RefactoringFactory factory = RefactoringFactory.getInstance(project);
      final RenameRefactoring renameRefactoring =
        factory.createRename(elementToRename, m_targetName, m_searchInStrings, m_searchInNonJavaFiles);
      renameRefactoring.run();
    }
  }
}