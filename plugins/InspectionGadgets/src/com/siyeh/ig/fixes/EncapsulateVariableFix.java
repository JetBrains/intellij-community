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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class EncapsulateVariableFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
        return InspectionGadgetsBundle.message("encapsulate.variable.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiField field = (PsiField) nameElement.getParent();
        final JavaRefactoringActionHandlerFactory factory =
                JavaRefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler renameHandler =
                factory.createEncapsulateFieldsHandler();
        renameHandler.invoke(project, new PsiElement[]{field}, null);
    }
}
