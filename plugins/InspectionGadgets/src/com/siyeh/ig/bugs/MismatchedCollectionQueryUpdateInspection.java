package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.VariableAssignedFromVisitor;
import com.siyeh.ig.psiutils.VariablePassedAsArgumentVisitor;
import com.siyeh.ig.psiutils.VariableReturnedVisitor;

public class MismatchedCollectionQueryUpdateInspection extends VariableInspection {
    public String getID(){
        return "MismatchedQueryAndUpdateOfCollection";
    }
    public String getDisplayName() {
        return "Mismatched query and update of collection";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        final PsiVariable variable = (PsiVariable) location.getParent();
        final PsiElement context;
        if (variable instanceof PsiField) {
            context = ((PsiMember) variable).getContainingClass();
        } else {
            context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        }
        final boolean updated = collectionContentsAreUpdated(variable, context);
        final boolean queried = collectionContentsAreQueried(variable, context);
        if (updated) {
            return "Contents of collection #ref are updated, but never queried #loc";
        } else if (queried) {
            return "Contents of collection #ref are queried, but never updated #loc";
        } else {
            return "Contents of collection #ref are neither queried nor updated #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MismatchedCollectionQueryUpdateVisitor(this, inspectionManager, onTheFly);
    }

    private static class MismatchedCollectionQueryUpdateVisitor extends BaseInspectionVisitor {
        private MismatchedCollectionQueryUpdateVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final PsiType type = field.getType();
            if (!CollectionUtils.isCollectionClassOrInterface(type)) {
                return;
            }
            final boolean written = collectionContentsAreUpdated(field, containingClass);
            final boolean read = collectionContentsAreQueried(field, containingClass);
            if (written && read) {
                return;
            }
            registerFieldError(field);
        }


        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);

            final PsiCodeBlock codeBlock =
                    (PsiCodeBlock) PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiType type = variable.getType();
            if (!CollectionUtils.isCollectionClassOrInterface(type)) {
                return;
            }
            final boolean written = collectionContentsAreUpdated(variable, codeBlock);
            final boolean read = collectionContentsAreQueried(variable, codeBlock);
            if (written && read) {
                return;
            }
            registerVariableError(variable);
        }

    }

    static boolean collectionContentsAreUpdated(PsiVariable variable, PsiElement context) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null && !isEmptyCollectionInitializer(initializer)) {
            return true;
        }
        if (variableIsAssigned(variable, context)) {
            return true;
        }
        if (variableIsAssignedFrom(variable, context)) {
            return true;
        }
        if (variableIsPassedAsMethodArgument(variable, context)) {
            return true;
        }
        if (collectionUpdateCalled(variable, context)) {
            return true;
        }
        return false;
    }

    static boolean collectionContentsAreQueried(PsiVariable variable, PsiElement context) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null && !isEmptyCollectionInitializer(initializer)) {
            return true;
        }
        if (variableIsAssigned(variable, context)) {
            return true;
        }
        if (variableIsAssignedFrom(variable, context)) {
            return true;
        }
        if (variableIsReturned(variable, context)) {
            return true;
        }
        if (variableIsPassedAsMethodArgument(variable, context)) {
            return true;
        }
        if (collectionQueryCalled(variable, context)) {
            return true;
        }
        return false;
    }

    private static boolean variableIsAssignedFrom(PsiVariable variable, PsiElement context) {
        final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }

    private static boolean variableIsPassedAsMethodArgument(PsiVariable variable, PsiElement context) {
        final VariablePassedAsArgumentVisitor visitor = new VariablePassedAsArgumentVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    private static boolean variableIsAssigned(PsiVariable variable, PsiElement context) {
        final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }


    private static boolean variableIsReturned(PsiVariable variable, PsiElement context) {
        final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable);
        context.accept(visitor);
        return visitor.isReturned();
    }

    private static boolean collectionQueryCalled(PsiVariable variable, PsiElement context) {
        final CollectionQueryCalledVisitor visitor = new CollectionQueryCalledVisitor(variable);
        context.accept(visitor);
        return visitor.isQueried();
    }

    private static boolean collectionUpdateCalled(PsiVariable variable, PsiElement context) {
        final CollectionUpdateCalledVisitor visitor = new CollectionUpdateCalledVisitor(variable);
        context.accept(visitor);
        return visitor.isUpdated();
    }

    private static boolean isEmptyCollectionInitializer(PsiExpression initializer) {
        if (!(initializer instanceof PsiNewExpression)) {
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) initializer;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return false;
        }
        final PsiExpression[] expressions = argumentList.getExpressions();
        for (int i = 0; i < expressions.length; i++) {
            final PsiExpression arg = expressions[i];
            final PsiType argType = arg.getType();
            if (argType == null) {
                return false;
            }
            if (CollectionUtils.isCollectionClassOrInterface(argType)) {
                return false;
            }
        }
        return true;
    }

}
