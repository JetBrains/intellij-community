package com.siyeh.igtest.bugs;

import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiElement;

public class CastConflictsWithInstanceofInspection {
    public void foo() {
        Number x = bar();

        System.out.println((Double) x);

        if (x instanceof Float) {
            System.out.println((Double) x);
        }
    }

    private Number bar() {
        return null;
    }

    void method(PsiElement p) {
        if (p instanceof PsiReferenceExpression) {
            PsiStatement stmt = (PsiStatement) p;
            PsiReferenceExpression ref = (PsiReferenceExpression) p;
        } else {
            PsiStatement stmt = (PsiStatement) p;

        }

    }

}
