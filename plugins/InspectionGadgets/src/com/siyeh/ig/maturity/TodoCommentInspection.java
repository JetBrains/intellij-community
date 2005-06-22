package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;

public class TodoCommentInspection extends ClassInspection {
    public String getDisplayName() {
        return "TODO comment";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "TODO comment #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutToStringVisitor();
    }

    private static class ClassWithoutToStringVisitor extends BaseInspectionVisitor {
        public void visitComment(PsiComment comment) {
            super.visitComment(comment);
            if (TodoUtil.isTodoComment(comment)) {
                registerError(comment);
            }
        }

    }

}
