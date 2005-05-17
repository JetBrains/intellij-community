package com.siyeh.ipp.psiutils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

public class ErrorUtil{
    private ErrorUtil(){
        super();
    }

    public static boolean containsError(PsiElement element){
        final ErrorElementVisitor visitor = new ErrorElementVisitor();
        element.accept(visitor);
        return visitor.containsErrorElement();
    }

    private static class ErrorElementVisitor extends PsiRecursiveElementVisitor{
        private boolean containsErrorElement = false;

        public void visitErrorElement(PsiErrorElement element){
            containsErrorElement = true;
        }

        private boolean containsErrorElement(){
            return containsErrorElement;
        }
    }
}
