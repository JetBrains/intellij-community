package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class SplitElseIfIntention extends Intention{
    public String getText(){
        return "Split else-if";
    }

    public String getFamilyName(){
        return "Split Else If";
    }

    public PsiElementPredicate getElementPredicate(){
        return new SplitElseIfPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{file.getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiJavaToken token =
                (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        final PsiStatement elseBranch = parentStatement.getElseBranch();
        final String newStatement = '{' + elseBranch.getText() + '}';
        replaceStatement(project, newStatement, elseBranch);
    }
}