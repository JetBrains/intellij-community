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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MismatchedArrayReadWriteInspection extends VariableInspection{
    public String getID(){
        return "MismatchedReadAndWriteOfArray";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("mismatched.read.write.array.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiVariable variable = (PsiVariable) location.getParent();
        assert variable!=null;
        final PsiElement context;
        if(variable instanceof PsiField){
            context = PsiUtil.getTopLevelClass(variable);
        } else{
            context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        }
        final boolean written = arrayContentsAreWritten(variable, context);
        if(written){
            return InspectionGadgetsBundle.message("mismatched.read.write.array.problem.descriptor.write.not.read");
        } else{
            return InspectionGadgetsBundle.message("mismatched.read.write.array.problem.descriptor.read.not.write");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedArrayReadWriteVisitor();
    }

    private static class MismatchedArrayReadWriteVisitor
                                                         extends BaseInspectionVisitor{

        public void visitField(@NotNull PsiField field){
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
            if(containingClass == null){
                return;
            }
            final PsiType type = field.getType();
            if(type.getArrayDimensions() == 0){
                return;
            }
            final boolean written = arrayContentsAreWritten(field,
                                                            containingClass);
            final boolean read = arrayContentsAreRead(field, containingClass);
            if(written != read){
                registerFieldError(field);
            }
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable){
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if(codeBlock == null){
                return;
            }
            final PsiType type = variable.getType();
            if(type.getArrayDimensions() == 0){
                return;
            }
            final boolean written = arrayContentsAreWritten(variable,
                                                            codeBlock);
            final boolean read = arrayContentsAreRead(variable, codeBlock);
            if(written && read){
                return;
            }
            if(!read && !written){
                return;
            }
            registerVariableError(variable);
        }
    }

    private static boolean arrayContentsAreWritten(PsiVariable variable,
                                                   PsiElement context){
        if(VariableAccessUtils.arrayContentsAreAssigned(variable, context)){
            return true;
        }
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isDefaultArrayInitializer(initializer)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssigned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsReturned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                                                                context)){
            return true;
        }
        return VariableAccessUtils.variableIsUsedInArrayInitializer(variable,
                                                                    context);
    }

    private static boolean isDefaultArrayInitializer(PsiExpression initializer){
        if(!(initializer instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) initializer;
        return newExpression.getArrayInitializer() == null;
    }

    private static boolean arrayContentsAreRead(PsiVariable variable,
                                                PsiElement context){
        if(VariableAccessUtils.arrayContentsAreAccessed(variable, context)){
            return true;
        }
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isDefaultArrayInitializer(initializer)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssigned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsReturned(variable, context)){
            return true;
        }
        if(VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                                                                context)){
            return true;
        }
        return VariableAccessUtils.variableIsUsedInArrayInitializer(variable,
                                                                    context);
    }
}

