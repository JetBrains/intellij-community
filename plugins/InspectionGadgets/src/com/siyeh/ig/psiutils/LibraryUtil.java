package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

public class LibraryUtil {
    private LibraryUtil() {
        super();
    }

    public static boolean classIsInLibrary(PsiClass aClass) {
        final PsiFile file = aClass.getContainingFile();
        final String fileName = file.getName();
        return !fileName.endsWith(".java");
    }
}

