package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

import java.util.ArrayList;
import java.util.List;

public class ForLoopWithMissingComponentInspection extends StatementInspection {

    public String getDisplayName() {
        return "'for' loop with missing components";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final List components = new ArrayList(3);
        final PsiJavaToken forToken = (PsiJavaToken) location;
        final PsiForStatement forStatement = (PsiForStatement) forToken.getParent();

        if (!hasInitializer(forStatement)) {
            components.add("initializer");
        }
        if (!hasCondition(forStatement)) {
            components.add("condition");
        }
        if (!hasUpdate(forStatement)) {
            components.add("update");
        }
        final String missingComponents;
        if (components.size() == 1) {
            missingComponents = (String) components.get(0);
        } else if (components.size() == 2) {
            missingComponents = components.get(0) + " and " + components.get(1);
        } else {
            missingComponents = components.get(0) + ", " + components.get(1) + " and " + components.get(2);
        }
        return "#ref statement lacks " + missingComponents + " #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ForLoopWithMissingComponentVisitor(this, inspectionManager, onTheFly);
    }

    private static class ForLoopWithMissingComponentVisitor extends BaseInspectionVisitor {
        private ForLoopWithMissingComponentVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);

            if (hasCondition(statement)
                    && hasInitializer(statement)
                    && hasUpdate(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }

    private static boolean hasCondition(PsiForStatement statement) {
        return statement.getCondition() != null;
    }

    private static boolean hasInitializer(PsiForStatement statement) {
        final PsiStatement initialization = statement.getInitialization();
        return initialization != null && !(initialization instanceof PsiEmptyStatement);
    }

    private static boolean hasUpdate(PsiForStatement statement) {
        final PsiStatement update = statement.getUpdate();
        return update != null && !(update instanceof PsiEmptyStatement);
    }

}
