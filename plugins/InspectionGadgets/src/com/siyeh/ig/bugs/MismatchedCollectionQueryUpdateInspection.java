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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspection
        extends VariableInspection{

    public String getID(){
        return "MismatchedQueryAndUpdateOfCollection";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "mismatched.update.collection.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final boolean updated = ((Boolean)infos[0]).booleanValue();
        if(updated){
            return InspectionGadgetsBundle.message(
                    "mismatched.update.collection.problem.descriptor.updated.not.queried");
        } else{
            return InspectionGadgetsBundle.message(
                    "mismatched.update.collection.problem.description.queried.not.updated");
        }
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedCollectionQueryUpdateVisitor();
    }

    static boolean isEmptyCollectionInitializer(
            PsiExpression initializer){
        if(!(initializer instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression =
                (PsiNewExpression) initializer;
        final PsiExpressionList argumentList =
                newExpression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] expressions = argumentList.getExpressions();
        for(final PsiExpression arg : expressions){
            final PsiType argType = arg.getType();
            if(argType == null){
                return false;
            }
            if(CollectionUtils.isCollectionClassOrInterface(argType)){
                return false;
            }
        }
        return true;
    }

    private static class MismatchedCollectionQueryUpdateVisitor
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
            if(!CollectionUtils.isCollectionClassOrInterface(type)){
                return;
            }
            final boolean written =
                    collectionContentsAreUpdated(field, containingClass);
            final boolean read =
                    collectionContentsAreQueried(field, containingClass);
            if(read == written){
                return;
            }
            registerFieldError(field, Boolean.valueOf(written));
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable){
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable,
		                    PsiCodeBlock.class);
            if(codeBlock == null){
                return;
            }
            final PsiType type = variable.getType();
            if(!CollectionUtils.isCollectionClassOrInterface(type)){
                return;
            }
            final boolean written =
                    collectionContentsAreUpdated(variable, codeBlock);
            final boolean read =
                    collectionContentsAreQueried(variable, codeBlock);
            if(read != written){
                registerVariableError(variable, Boolean.valueOf(written));
            }
        }

        private static boolean collectionContentsAreUpdated(
                PsiVariable variable, PsiElement context){
            if(collectionUpdateCalled(variable, context)){
                return true;
            }
            final PsiExpression initializer = variable.getInitializer();
            if(initializer != null &&
                    !isEmptyCollectionInitializer(initializer)){
                return true;
            }
            if(initializer instanceof PsiNewExpression){
                final PsiNewExpression newExpression =
                        (PsiNewExpression)initializer;
                final PsiAnonymousClass anonymousClass =
                        newExpression.getAnonymousClass();
                if(anonymousClass != null){
                    if(collectionUpdateCalled(variable, anonymousClass)){
                        return true;
                    }
                }
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

        private static boolean collectionContentsAreQueried(
                PsiVariable variable, PsiElement context){
            if(collectionQueryCalled(variable, context)){
                return true;
            }
            final PsiExpression initializer = variable.getInitializer();
            if(initializer != null &&
                    !isEmptyCollectionInitializer(initializer)){
                return true;
            }
            if(collectionQueriedByAssignment(variable, context)){
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

        private static boolean collectionQueryCalled(PsiVariable variable,
                                                     PsiElement context){
            final CollectionQueryCalledVisitor visitor =
                    new CollectionQueryCalledVisitor(variable);
            context.accept(visitor);
            return visitor.isQueried();
        }

        private static boolean collectionUpdateCalled(PsiVariable variable,
                                                      PsiElement context){
            final CollectionUpdateCalledVisitor visitor =
                    new CollectionUpdateCalledVisitor(variable);
            context.accept(visitor);
            return visitor.isUpdated();
        }
    }

    public static boolean collectionQueriedByAssignment(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final CollectionQueriedByAssignmentVisitor visitor =
                new CollectionQueriedByAssignmentVisitor(variable);
        context.accept(visitor);
        return visitor.mayBeQueried();
    }

    private static class CollectionQueriedByAssignmentVisitor
            extends PsiRecursiveElementVisitor{

        private boolean mayBeQueried = false;
        @NotNull private final PsiVariable variable;

        CollectionQueriedByAssignmentVisitor(@NotNull PsiVariable variable){
            super();
            this.variable = variable;
        }

        public void visitElement(@NotNull PsiElement element){
            if (mayBeQueried) {
                return;
            }
            super.visitElement(element);
        }

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            if(mayBeQueried){
                return;
            }
            super.visitAssignmentExpression(assignment);
            final PsiExpression lhs = assignment.getLExpression();
            if (!VariableAccessUtils.mayEvaluateToVariable(lhs, variable)) {
                return;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (isEmptyCollectionInitializer(rhs)) {
                return;
            }
            mayBeQueried = true;
        }

        public boolean mayBeQueried(){
            return mayBeQueried;
        }
    }
}