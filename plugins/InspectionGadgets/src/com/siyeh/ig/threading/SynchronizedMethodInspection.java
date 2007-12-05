/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SynchronizedMethodInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_includeNativeMethods = true;


    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "synchronized.method.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiMethod method = (PsiMethod)infos[0];
        return InspectionGadgetsBundle.message(
                "synchronized.method.problem.descriptor", method.getName());
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiElement modifierList = location.getParent();
        assert modifierList != null;
        final PsiMethod method = (PsiMethod)modifierList.getParent();
        assert method != null;
        if (method.getBody() == null) {
            return null;
        }
        return new SynchronizedMethodFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizedMethodVisitor();
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "synchronized.method.include.option"),
                this, "m_includeNativeMethods");
    }

    private static class SynchronizedMethodFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "synchronized.method.move.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement nameElement = descriptor.getPsiElement();
            final PsiModifierList modiferList =
                    (PsiModifierList)nameElement.getParent();
            assert modiferList != null;
            final PsiMethod method = (PsiMethod)modiferList.getParent();
            modiferList.setModifierProperty(PsiModifier.SYNCHRONIZED, false);
            assert method != null;
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final String text = body.getText();
            @NonNls final String replacementText;
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                final PsiClass containingClass = method.getContainingClass();
                assert containingClass != null;
                final String className = containingClass.getName();
                replacementText = "{ synchronized(" + className + ".class){" +
                        text.substring(1) + '}';
            } else {
                replacementText = "{ synchronized(this){" + text.substring(1) +
                        '}';
            }
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiCodeBlock block =
                    elementFactory.createCodeBlockFromText(replacementText,
                            null);
            body.replace(block);
            final CodeStyleManager codeStyleManager =
                    psiManager.getCodeStyleManager();
            codeStyleManager.reformat(method);
        }
    }

    private class SynchronizedMethodVisitor extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return;
            }
            if (!m_includeNativeMethods &&
                    method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            registerModifierError(PsiModifier.SYNCHRONIZED, method, method);
        }
    }
}