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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ParameterHidingMemberVariableInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreInvisibleFields = true;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodParametersHidingInstanceFields = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreForConstructors = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreForPropertySetters = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreForAbstractMethods = false;

    @NotNull
    public String getID(){
        return "ParameterHidesMemberVariable";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.display.name");
    }

    protected InspectionGadgetsFix buildFix(Object... infos){
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.problem.descriptor");
    }

    public JComponent createOptionsPanel(){
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.ignore.setters.option"),
                "m_ignoreForPropertySetters");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.ignore.superclass.option"),
                "m_ignoreInvisibleFields");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.ignore.constructors.option"),
                "m_ignoreForConstructors");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.ignore.abstract.methods.option"),
                "m_ignoreForAbstractMethods");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.hides.member.variable.ignore.static.parameters.option"),
                "m_ignoreStaticMethodParametersHidingInstanceFields");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ParameterHidingMemberVariableVisitor();
    }

    private class ParameterHidingMemberVariableVisitor
            extends BaseInspectionVisitor{

        @Override public void visitParameter(@NotNull PsiParameter variable){
            super.visitParameter(variable);
            if(variable.getDeclarationScope() instanceof PsiCatchSection){
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(variable,
                            PsiMethod.class);
            if(method == null){
                return;
            }
            if(m_ignoreForConstructors && method.isConstructor()){
                return;
            }
            if(m_ignoreForAbstractMethods) {
                if(method.hasModifierProperty(PsiModifier.ABSTRACT)){
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                if(containingClass.isInterface()){
                    return;
                }
            }
            if(m_ignoreForPropertySetters){
                final String methodName = method.getName();
                final PsiType returnType = method.getReturnType();
                if(methodName.startsWith(HardcodedMethodConstants.SET) &&
                        PsiType.VOID.equals(returnType)){
                    return;
                }
            }
            final PsiClass aClass =
                    ClassUtils.getContainingClass(variable);
            if(aClass == null){
                return;
            }
            final String variableName = variable.getName();
            final PsiField[] fields = aClass.getAllFields();
            for(final PsiField field : fields){
                if(checkFieldName(field, variableName, aClass)){
                    if(m_ignoreStaticMethodParametersHidingInstanceFields &&
                            !field.hasModifierProperty(PsiModifier.STATIC) &&
                            method.hasModifierProperty(PsiModifier.STATIC)){
                        continue;
                    }
                    registerVariableError(variable);
                }
            }
        }

        private boolean checkFieldName(PsiField field, String variableName,
                                       PsiClass aClass){
            if(field == null){
                return false;
            }
            final String fieldName = field.getName();
            if(fieldName == null){
                return false;
            }
            if(!fieldName.equals(variableName)){
                return false;
            }
            return !m_ignoreInvisibleFields ||
                    ClassUtils.isFieldVisible(field, aClass);
        }
    }
}