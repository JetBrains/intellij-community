/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class MethodMayBeStaticInspection extends MethodInspection{

    /**
     * @noinspection PublicField
     */
    public boolean m_onlyPrivateOrFinal = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreEmptyMethods = true;

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "method.may.be.static.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "method.may.be.static.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new MethodMayBeStaticFix();
    }

    private static class MethodMayBeStaticFix extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message("make.static.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiJavaToken classNameToken = (PsiJavaToken)
                    descriptor.getPsiElement();
            final PsiMethod innerClass = (PsiMethod) classNameToken.getParent();
            assert innerClass != null;
            final PsiModifierList modifiers = innerClass.getModifierList();
            modifiers.setModifierProperty(PsiModifier.STATIC, true);
        }
    }

    public JComponent createOptionsPanel(){
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "method.may.be.static.only.option"), "m_onlyPrivateOrFinal");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "method.may.be.static.empty.option"), "m_ignoreEmptyMethods");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MethodCanBeStaticVisitor();
    }

    private class MethodCanBeStaticVisitor extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.STATIC) ||
                method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if(method.isConstructor() || method.getNameIdentifier() == null) {
                return;
            }
            if(m_ignoreEmptyMethods && MethodUtils.isEmpty(method)){
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(method);
            if(containingClass == null){
                return;
            }
            final PsiElement scope = containingClass.getScope();
            if(!(scope instanceof PsiJavaFile) &&
                    !containingClass.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if(m_onlyPrivateOrFinal &&
                    !method.hasModifierProperty(PsiModifier.FINAL) &&
                    !method.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            if(TestUtils.isJUnitTestMethod(method)){
                return;
            }

            if (SuperMethodsSearch.search(method, null, true, false).findFirst() != null) {
                return;
            }

            if(MethodUtils.isOverridden(method)){
                return;
            }
            final MethodReferenceVisitor visitor =
                    new MethodReferenceVisitor(method);
            method.accept(visitor);
            if(!visitor.areReferencesStaticallyAccessible()){
                return;
            }
            registerMethodError(method);
        }
    }
}