package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class StringReplaceableByStringBufferInspection extends ExpressionInspection {
    public String getID(){
        return "NonConstantStringShouldBeStringBuffer";
    }

    public String getDisplayName() {
        return "Non-constant String should be StringBuffer";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-constant String #ref should probably be declared as StringBuffer #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringReplaceableByStringBufferVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringReplaceableByStringBufferVisitor extends BaseInspectionVisitor {
        private StringReplaceableByStringBufferVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiType type = variable.getType();
            if (!TypeUtils.typeEquals("java.lang.String", type)) {
                return;
            }
            if (!variableIsAppendedTo(variable, codeBlock)) {
                return;
            }
            registerVariableError(variable);
        }

        public static boolean variableIsAppendedTo(PsiVariable variable, PsiElement context) {
            final StringVariableIsAppendedToVisitor visitor = new StringVariableIsAppendedToVisitor(variable);
            context.accept(visitor);
            return visitor.isAppendedTo();
        }

    }

}
