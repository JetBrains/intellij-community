package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.VariableAccessUtils;

public class MismatchedArrayReadWriteInspection extends VariableInspection {

    public String getDisplayName() {
        return "Mismatched read and write of array";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiVariable variable = (PsiVariable) location.getParent();
        final PsiElement context;
        if (variable instanceof PsiField) {
            context = ((PsiMember) variable).getContainingClass();
        } else {
            context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        }
        final boolean written = arrayContentsAreWritten(variable, context);
        final boolean read = arrayContentsAreRead(variable, context);
        if (written) {
            return "Contents of array #ref are written to, but never read #loc";
        } else if (read) {
            return "Contents of array #ref are read, but never written to #loc";
        } else {
            return "Contents of array #ref are neither read nor written to #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MismatchedArrayReadWriteVisitor(this, inspectionManager, onTheFly);
    }

    private static class MismatchedArrayReadWriteVisitor extends BaseInspectionVisitor {
        private MismatchedArrayReadWriteVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            if (type.getArrayDimensions() == 0) {
                return;
            }
            final boolean written = arrayContentsAreWritten(field, containingClass);
            final boolean read = arrayContentsAreRead(field, containingClass);
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
            if (type.getArrayDimensions() == 0) {
                return;
            }
            final boolean written = arrayContentsAreWritten(variable, codeBlock);
            final boolean read = arrayContentsAreRead(variable, codeBlock);
            if (written && read) {
                return;
            }
            registerVariableError(variable);
        }

    }

    static boolean arrayContentsAreWritten(PsiVariable variable, PsiElement context) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null && !isDefaultArrayInitializer(initializer)) {
            return true;
        }
        if (VariableAccessUtils.variableIsAssigned(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.arrayContentsAreAssigned(variable, context)) {
            return true;
        }
        return false;
    }

    private static boolean isDefaultArrayInitializer(PsiExpression initializer) {
        if (!(initializer instanceof PsiNewExpression)) {
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) initializer;
        if (newExpression.getArrayInitializer() != null) {
            return false;
        }
        return true;
    }

    public static boolean arrayContentsAreRead(PsiVariable variable, PsiElement context) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null && !isDefaultArrayInitializer(initializer)) {
            return true;
        }
        if (VariableAccessUtils.variableIsAssigned(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.variableIsReturned(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context)) {
            return true;
        }
        if (VariableAccessUtils.arrayContentsAreAccessed(variable, context)) {
            return true;
        }
        return false;
    }


}
