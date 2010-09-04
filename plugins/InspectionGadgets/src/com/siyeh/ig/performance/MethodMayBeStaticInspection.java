/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MethodMayBeStaticInspection extends BaseInspection {

    /**
     * @noinspection PublicField
     */
    public boolean m_onlyPrivateOrFinal = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreEmptyMethods = true;

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "method.may.be.static.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "method.may.be.static.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos){
        return new ChangeModifierFix(PsiModifier.STATIC);
    }

    @Override
    public JComponent createOptionsPanel(){
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "method.may.be.static.only.option"), "m_onlyPrivateOrFinal");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "method.may.be.static.empty.option"), "m_ignoreEmptyMethods");
        return optionsPanel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new MethodCanBeStaticVisitor();
    }

    private class MethodCanBeStaticVisitor extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.STATIC) ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
                return;
            }
            if(method.isConstructor() || method.getNameIdentifier() == null){
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
            final ExtensionsArea rootArea = Extensions.getRootArea();
            final ExtensionPoint<Condition<PsiMember>> extensionPoint = rootArea.getExtensionPoint(
                    "com.intellij.cantBeStatic");
            final Condition<PsiMember>[] addins = extensionPoint.getExtensions();
            for (Condition<PsiMember> addin : addins) {
              if (addin.value(method)) {
                    return;
                }
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
           
            final Query<MethodSignatureBackedByPsiMethod> superMethodQuery =
                    SuperMethodsSearch.search(method, null, true, false);
            if (superMethodQuery.findFirst() != null) {
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
