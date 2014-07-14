/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;

public class RenameFix extends GroovyFix {

  private final String targetName;

  public RenameFix() {
    super();
    targetName = null;
  }

  public RenameFix(@NonNls String targetName) {
    super();
    this.targetName = targetName;
  }

  @Override
  @NotNull
  public String getName() {
    if (targetName == null) {
      return "Rename";
    } else {
      return "Rename to " + targetName;
    }
  }

  @Override
  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiElement nameIdentifier = descriptor.getPsiElement();
    final PsiElement elementToRename = nameIdentifier.getParent();
    if (targetName == null) {
      final RefactoringActionHandlerFactory factory =
          RefactoringActionHandlerFactory.getInstance();
      final RefactoringActionHandler renameHandler =
          factory.createRenameHandler();
      final DataManager dataManager = DataManager.getInstance();
      final DataContext dataContext = dataManager.getDataContext();
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          renameHandler.invoke(project, new PsiElement[]{elementToRename},
                               dataContext);
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
      }
    } else {
      final RefactoringFactory factory =
          RefactoringFactory.getInstance(project);
      final RenameRefactoring renameRefactoring =
          factory.createRename(elementToRename, targetName);
      renameRefactoring.run();
    }
  }
}