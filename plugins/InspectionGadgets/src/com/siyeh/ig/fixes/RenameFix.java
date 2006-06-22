/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenameFix extends InspectionGadgetsFix {

    private final String m_targetName;

    public RenameFix() {
        super();
        m_targetName = null;
    }

    public RenameFix(@NonNls String targetName) {
        super();
        m_targetName = targetName;
    }

    @NotNull
    public String getName() {
        if (m_targetName == null) {
            return InspectionGadgetsBundle.message("rename.quickfix");
        } else {
            return InspectionGadgetsBundle.message("renameto.quickfix",
                    m_targetName);
        }
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement nameIdentifier = descriptor.getPsiElement();
        final PsiElement elementToRename = nameIdentifier.getParent();
        if (m_targetName == null) {
            final RefactoringActionHandlerFactory factory =
                    RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler renameHandler =
                    factory.createRenameHandler();
            final DataManager dataManager = DataManager.getInstance();
            final DataContext dataContext = dataManager.getDataContext();
            renameHandler.invoke(project, new PsiElement[]{elementToRename},
                    dataContext);
        } else {
            final RefactoringFactory factory =
                    RefactoringFactory.getInstance(project);
            final RenameRefactoring renameRefactoring =
                    factory.createRename(elementToRename, m_targetName);
            renameRefactoring.run();
        }
    }
}