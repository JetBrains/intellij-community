package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class EmptyInitializerInspection extends StatementInspection {
    public String getID(){
        return "EmptyClassInitializer";
    }
    public String getDisplayName() {
        return "Empty class initializer";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Empty class initializer #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EmptyInitializerVisitor(this, inspectionManager, onTheFly);
    }

    private static class EmptyInitializerVisitor extends BaseInspectionVisitor {
        private EmptyInitializerVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClassInitializer(PsiClassInitializer initializer) {
            super.visitClassInitializer(initializer);
            final PsiCodeBlock body = initializer.getBody();
            if (!codeBlockIsEmpty(body)) {
                return;
            }
            registerError(body.getLBrace());
        }


        private static boolean codeBlockIsEmpty(PsiCodeBlock codeBlock) {
            final PsiStatement[] statements = codeBlock.getStatements();
            return statements.length == 0;
        }
    }
}
