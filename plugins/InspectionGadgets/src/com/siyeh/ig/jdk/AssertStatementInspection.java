package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;

public class AssertStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'assert' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssertStatementVisitor();
    }

    private static class AssertStatementVisitor extends StatementInspectionVisitor {

        public void visitAssertStatement(PsiAssertStatement statement) {
            super.visitAssertStatement(statement);
            registerStatementError(statement);
        }

    }

}