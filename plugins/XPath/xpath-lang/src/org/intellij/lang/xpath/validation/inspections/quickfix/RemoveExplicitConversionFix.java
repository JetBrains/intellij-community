/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.validation.inspections.quickfix;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class RemoveExplicitConversionFix extends ReplaceElementFix<XPathExpression> {

    public RemoveExplicitConversionFix(XPathExpression expression) {
        super(ExpectedTypeUtil.unparenthesize(expression));
    }

    @NotNull
    public String getName() {
        return "Remove Explicit Type Conversion";
    }

    @NotNull
    public String getFamilyName() {
        return "ImplicitTypeConversion";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!super.isAvailable(project, editor, file)) return false;
        return ((XPathFunctionCall)myElement).getArgumentList().length == 1;
    }

    public void invokeImpl(Project project, PsiFile file) throws IncorrectOperationException {
        final XPathExpression arg0 = ((XPathFunctionCall)myElement).getArgumentList()[0];
        final XPathExpression outer = PsiTreeUtil.getParentOfType(myElement, XPathExpression.class);
        if (arg0 instanceof XPathBinaryExpression && outer instanceof XPathBinaryExpression) {
            // TODO make this smarter by determining operator precedence
            replace("(" + arg0.getText() + ")");
        } else {
            replace(arg0.getText());
        }
    }
}
