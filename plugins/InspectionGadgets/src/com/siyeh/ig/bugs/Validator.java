package com.siyeh.ig.bugs;

import com.intellij.psi.PsiType;

interface Validator{
    boolean valid(PsiType type);

    String type();
}
