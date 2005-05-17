package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new DefaultNotLastCaseInSwitchVisitor();
    }

    private static class DefaultNotLastCaseInSwitchVisitor extends StatementInspectionVisitor {

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
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