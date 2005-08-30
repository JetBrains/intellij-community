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
package com.siyeh.ig.finalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class FinalizeNotProtectedInspection extends MethodInspection{
    private final ProtectedFinalizeFix fix = new ProtectedFinalizeFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("finalize.not.declared.protected.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("finalize.not.declared.protected.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new FinalizeDeclaredProtectedVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ProtectedFinalizeFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("finalize.not.declared.protected.make.protected.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod) methodName.getParent();
            assert method != null;
            final PsiModifierList modifiers = method.getModifierList();
            modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
            modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
            modifiers.setModifierProperty(PsiModifier.PROTECTED, true);
        }
    }

    private static class FinalizeDeclaredProtectedVisitor
            extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!HardcodedMethodConstants.FINALIZE.equals(methodName)) {
              return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList.getParameters().length != 0){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PROTECTED)){
                return;
            }
            registerMethodError(method);
        }
    }
}
