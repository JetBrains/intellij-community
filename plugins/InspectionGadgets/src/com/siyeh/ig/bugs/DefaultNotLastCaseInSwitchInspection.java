package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class DefaultNotLastCaseInSwitchInspection extends StatementInspection {

    public String getDisplayName() {
        return "'default' not last case in 'switch'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' branch not last case in 'switch' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new DefaultNotLastCaseInSwitchVisitor(this, inspectionManager, onTheFly);
    }

    private static class DefaultNotLastCaseInSwitchVisitor extends StatementInspectionVisitor {
        private DefaultNotLastCaseInSwitchVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            boolean labelSeen = false;
            for (int i = statements.length - 1; i >= 0; i--) {
                final PsiStatement child = statements[i];
                if (child instanceof PsiSwitchLabelStatement) {
                    final PsiSwitchLabelStatement label = (PsiSwitchLabelStatement) child;
                    if (label.isDefaultCase()) {
                        if (labelSeen) {
                            registerStatementError(label);
                        }
                        return;
                    } else {
                        labelSeen = true;
                    }
                }
            }
        }
    }

}