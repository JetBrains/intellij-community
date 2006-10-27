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
package com.siyeh.ig.finalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FinalizeCallsSuperFinalizeInspection extends MethodInspection{

    @SuppressWarnings("PublicField")
    public boolean m_ignoreForObjectSubclasses = false;

    public String getID(){
        return "FinalizeDoesntCallSuperFinalize";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "finalize.doesnt.call.super.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.FINALIZATION_GROUP_NAME;
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
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "finalize.doesnt.call.super.ignore.option"),
                this, "m_ignoreForObjectSubclasses");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NoExplicitFinalizeCallsVisitor();
    }

    private class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
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
            if(m_ignoreForObjectSubclasses){
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
            registerMethodError(method);
        }
    }
}