package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;

public class SwitchUtils{
    private SwitchUtils(){
        super();
    }

    public static int calculateBranchCount(PsiSwitchStatement statement){
    final PsiCodeBlock body = statement.getBody();
        int branches = 0;
        final PsiStatement[] statements = body.getStatements();
        for (int i = 0; i < statements.length; i++) {
            final PsiStatement child = statements[i];
            if (child instanceof PsiSwitchLabelStatement) {
                branches++;
            }
        }
        return branches;
    }
}
