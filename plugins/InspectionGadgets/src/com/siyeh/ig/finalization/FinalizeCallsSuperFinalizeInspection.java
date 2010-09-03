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
package com.siyeh.ig.finalization;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FinalizeCallsSuperFinalizeInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean ignoreObjectSubclasses = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreTrivialFinalizers = true;

    @NotNull
    public String getID(){
        return "FinalizeDoesntCallSuperFinalize";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "finalize.doesnt.call.super.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "finalize.doesnt.call.super.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public JComponent createOptionsPanel(){
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "finalize.doesnt.call.super.ignore.option"),
                "ignoreObjectSubclasses");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "ignore.trivial.finalizers.option"), "ignoreTrivialFinalizers");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NoExplicitFinalizeCallsVisitor();
    }

    private class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!HardcodedMethodConstants.FINALIZE.equals(methodName)){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.NATIVE) ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(ignoreObjectSubclasses){
                final PsiClass superClass = containingClass.getSuperClass();
                if(superClass != null){
                    final String superClassName = superClass.getQualifiedName();
                    if("java.lang.Object".equals(superClassName)){
                        return;
                    }
                }
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList.getParametersCount() != 0){
                return;
            }
            final CallToSuperFinalizeVisitor visitor =
                    new CallToSuperFinalizeVisitor();
            method.accept(visitor);
            if(visitor.isCallToSuperFinalizeFound()){
                return;
            }
            if(ignoreTrivialFinalizers && isTrivial(method)){
                return;
            }
            registerMethodError(method);
        }

        private boolean isTrivial(PsiMethod method) {
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return true;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true;
            }
            final Project project = method.getProject();
            final JavaPsiFacade psiFacade =
                    JavaPsiFacade.getInstance(project);
            final PsiConstantEvaluationHelper evaluationHelper =
                    psiFacade.getConstantEvaluationHelper();
            for (PsiStatement statement : statements) {
                if (!(statement instanceof PsiIfStatement)) {
                    return false;
                }
                final PsiIfStatement ifStatement =
                        (PsiIfStatement) statement;
                final PsiExpression condition = ifStatement.getCondition();
                final Object result =
                        evaluationHelper.computeConstantExpression(condition);
                if (result == null || !result.equals(Boolean.FALSE)) {
                    return false;
                }
            }
            return true;
        }
    }
}