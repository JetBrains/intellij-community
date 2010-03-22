/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

class ConvertOctalLiteralToDecimalFix
        extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
        return InspectionGadgetsBundle.message(
                "convert.octal.literal.to.decimal.literal.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
        final PsiElement element = descriptor.getPsiElement();
        final String text = element.getText();
        final int number = Integer.parseInt(text, 8);
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiExpression decimalNumber =
                factory.createExpressionFromText(Integer.toString(number),
                        element);
        element.replace(decimalNumber);
    }
}
