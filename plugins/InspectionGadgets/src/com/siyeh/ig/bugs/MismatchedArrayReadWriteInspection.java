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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class MismatchedArrayReadWriteInspection extends BaseInspection {

    @NotNull
    public String getID(){
        return "MismatchedReadAndWriteOfArray";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "mismatched.read.write.array.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final boolean written = ((Boolean)infos[0]).booleanValue();
        if(written){
            return InspectionGadgetsBundle.message(
                    "mismatched.read.write.array.problem.descriptor.write.not.read");
        } else{
            return InspectionGadgetsBundle.message(
                    "mismatched.read.write.array.problem.descriptor.read.not.write");
        }
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedArrayReadWriteVisitor();
    }

    private static class MismatchedArrayReadWriteVisitor
            extends BaseInspectionVisitor{

        @Override public void visitField(@NotNull PsiField field){
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
            if (written == read) {
                return;
            }
            registerFieldError(field, Boolean.valueOf(written));
        }

        @Override public void visitLocalVariable(@NotNull PsiLocalVariable variable){
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
            final boolean written =
                    arrayContentsAreWritten(variable, codeBlock);
            final boolean read = arrayContentsAreRead(variable, codeBlock);
            if(written == read){
                return;
            }
            registerVariableError(variable, Boolean.valueOf(written));
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

        private static boolean isDefaultArrayInitializer(
                PsiExpression initializer){
            if(!(initializer instanceof PsiNewExpression)){
                return false;
            }
            final PsiNewExpression newExpression =
                    (PsiNewExpression) initializer;
            return newExpression.getArrayInitializer() == null;
        }
    }

  public boolean runForWholeFile() {
    return true;
  }
}