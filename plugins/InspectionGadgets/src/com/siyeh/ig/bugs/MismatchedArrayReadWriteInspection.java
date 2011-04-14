/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MismatchedArrayReadWriteInspection extends BaseInspection{

    @Override
    @NotNull
    public String getID(){
        return "MismatchedReadAndWriteOfArray";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "mismatched.read.write.array.display.name");
    }

    @Override
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

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    public boolean runForWholeFile(){
        return true;
    }
    
    @Override
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
            if(!checkVariable(field, containingClass)){
                return;
            }
            final boolean written =
                    arrayContentsAreWritten(field, containingClass);
            final boolean read = arrayContentsAreRead(field, containingClass);
            if (written == read){
                return;
            }
            registerFieldError(field, Boolean.valueOf(written));
        }

        @Override public void visitLocalVariable(
                @NotNull PsiLocalVariable variable){
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if(!checkVariable(variable, codeBlock)){
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

        private static boolean checkVariable(PsiVariable variable,
                                             PsiElement context) {
            if(context == null){
                return false;
            }
            final PsiType type = variable.getType();
            if(type.getArrayDimensions() == 0){
                return false;
            }
            if(VariableAccessUtils.variableIsAssigned(variable, context)){
                return false;
            }
            if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
                return false;
            }
            if(VariableAccessUtils.variableIsReturned(variable, context)){
                return false;
            }
            return !VariableAccessUtils.variableIsUsedInArrayInitializer(
                    variable, context);
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
            return variableIsWrittenAsMethodArgument(variable, context);
        }

        private static boolean arrayContentsAreRead(PsiVariable variable,
                                                    PsiElement context){
            if(VariableAccessUtils.arrayContentsAreAccessed(variable, context)){
                return true;
            }
            return variableIsReadAsMethodArgument(variable, context);
        }

        private static boolean isDefaultArrayInitializer(
                PsiExpression initializer){
            if (initializer instanceof PsiNewExpression) {
                final PsiNewExpression newExpression =
                        (PsiNewExpression) initializer;
                final PsiArrayInitializerExpression arrayInitializer =
                        newExpression.getArrayInitializer();
                return arrayInitializer == null ||
                        isDefaultArrayInitializer(arrayInitializer);
            } else if (initializer instanceof PsiArrayInitializerExpression) {
                final PsiArrayInitializerExpression arrayInitializerExpression =
                        (PsiArrayInitializerExpression) initializer;
                final PsiExpression[] initializers =
                        arrayInitializerExpression.getInitializers();
                return initializers.length == 0;
            }
            return false;
        }

        public static boolean variableIsWrittenAsMethodArgument(
                @NotNull PsiVariable variable, @NotNull PsiElement context){
            final VariablePassedAsArgumentVisitor visitor =
                    new VariablePassedAsArgumentVisitor(variable, true);
            context.accept(visitor);
            return visitor.isPassed();
        }

        public static boolean variableIsReadAsMethodArgument(
                @NotNull PsiVariable variable, @NotNull PsiElement context){
            final VariablePassedAsArgumentVisitor visitor =
                    new VariablePassedAsArgumentVisitor(variable, false);
            context.accept(visitor);
            return visitor.isPassed();
        }

        static class VariablePassedAsArgumentVisitor
                extends JavaRecursiveElementVisitor{

            @NotNull
            private final PsiVariable variable;
            private final boolean write;
            private boolean passed = false;

            VariablePassedAsArgumentVisitor(
                    @NotNull PsiVariable variable, boolean write){
                this.variable = variable;
                this.write = write;
            }

            @Override public void visitElement(@NotNull PsiElement element){
                if(!passed){
                    super.visitElement(element);
                }
            }

            @Override public void visitMethodCallExpression(
                    @NotNull PsiMethodCallExpression call){
                if(passed){
                    return;
                }
                super.visitMethodCallExpression(call);
                final PsiExpressionList argumentList = call.getArgumentList();
                final PsiExpression[] arguments = argumentList.getExpressions();
                for(int i = 0; i < arguments.length; i++){
                    final PsiExpression argument = arguments[i];
                    if(VariableAccessUtils.mayEvaluateToVariable(argument,
                            variable)){
                        if(write && i == 0 && isCallToSystemArraycopy(call)){
                            return;
                        }
                        if(!write && i == 2 && isCallToSystemArraycopy(call)){
                            return;
                        }
                        passed = true;
                    }
                }
            }

            private static boolean isCallToSystemArraycopy(
                    PsiMethodCallExpression call){
                final PsiReferenceExpression methodExpression =
                        call.getMethodExpression();
                @NonNls final String name =
                        methodExpression.getReferenceName();
                if(!"arraycopy".equals(name)){
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if(!(qualifier instanceof PsiReferenceExpression)){
                    return false;
                }
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifier;
                final PsiElement element =
                        referenceExpression.resolve();
                if(!(element instanceof PsiClass)){
                    return false;
                }
                final PsiClass aClass = (PsiClass) element;
                final String qualifiedName =
                        aClass.getQualifiedName();
                return "java.lang.System".equals(qualifiedName);
            }

            @Override public void visitNewExpression(
                    @NotNull PsiNewExpression newExpression){
                if(passed){
                    return;
                }
                super.visitNewExpression(newExpression);
                final PsiExpressionList argumentList =
                        newExpression.getArgumentList();
                if(argumentList == null){
                    return;
                }
                final PsiExpression[] arguments = argumentList.getExpressions();
                for(final PsiExpression argument : arguments){
                    if(VariableAccessUtils.mayEvaluateToVariable(argument,
                            variable)){
                        passed = true;
                    }
                }
            }

            public boolean isPassed(){
                return passed;
            }
        }
    }
}