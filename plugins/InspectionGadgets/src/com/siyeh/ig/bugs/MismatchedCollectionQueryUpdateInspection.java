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
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspection
        extends VariableInspection{
    public String getID(){
        return "MismatchedQueryAndUpdateOfCollection";
    }

    public String getDisplayName(){
        return "Mismatched query and update of collection";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiVariable variable = (PsiVariable) location.getParent();
        assert variable != null;
        final PsiElement context;
        if(variable instanceof PsiField){
            context = PsiUtil.getTopLevelClass(variable);
        } else{
            context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        }
        final boolean updated = collectionContentsAreUpdated(variable, context);
        if(updated){
            return "Contents of collection #ref are updated, but never queried #loc";
        } else{
            return "Contents of collection #ref are queried, but never updated #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedCollectionQueryUpdateVisitor();
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
            if( read!=written){
                registerFieldError(field);
            }
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
            if(read!=written){
                registerVariableError(variable);
            }
        }
    }

    private static boolean collectionContentsAreUpdated(PsiVariable variable,
                                                        PsiElement context){
        if(collectionUpdateCalled(variable, context)){
            return true;
        }
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isEmptyCollectionInitializer(initializer)){
            return true;
        }
	    if (initializer instanceof PsiNewExpression) {
		    final PsiNewExpression newExpression = (PsiNewExpression)initializer;
		    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
		    if (anonymousClass != null) {
			    if (collectionUpdateCalled(variable, anonymousClass)) {
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


    private static boolean collectionContentsAreQueried(PsiVariable variable,
                                                        PsiElement context){
        if(collectionQueryCalled(variable, context)){
            return true;
        }
	    final PsiExpression initializer = variable.getInitializer();
        if(initializer != null && !isEmptyCollectionInitializer(initializer)){
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

    private static boolean isEmptyCollectionInitializer(PsiExpression initializer){
        if(!(initializer instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) initializer;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
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
}
