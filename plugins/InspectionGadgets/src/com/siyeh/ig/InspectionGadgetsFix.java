package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionGadgetsFix implements LocalQuickFix{
    protected void deleteElement(@NotNull PsiElement element){
        try{
            element.delete();
        } catch(IncorrectOperationException e){
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected void replaceExpression(PsiExpression expression,
                                     String newExpression){
        try{
            final PsiManager psiManager = expression.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiExpression newExp = factory.createExpressionFromText(newExpression,
                                                                          null);
            final PsiElement replacementExp = expression.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch(IncorrectOperationException e){
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected void replaceStatement(PsiStatement statement,
                                    String newStatement){
        try{
            final PsiManager psiManager = statement.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiStatement newExp = factory.createStatementFromText(newStatement,
                                                                        null);
            final PsiElement replacementExp = statement.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch(IncorrectOperationException e){
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    public static boolean isQuickFixOnReadOnlyFile(ProblemDescriptor descriptor){
        final PsiElement problemElement = descriptor.getPsiElement();
        if(problemElement == null){
            return false;
        }
        final PsiFile containingPsiFile = problemElement.getContainingFile();
        if(containingPsiFile == null){
            return false;
        }
        final VirtualFile virtualFile = containingPsiFile.getVirtualFile();
        final PsiManager psiManager = problemElement.getManager();
        final Project project = psiManager.getProject();
        final ReadonlyStatusHandler handler = ReadonlyStatusHandler.getInstance(project);
        return handler.ensureFilesWritable(new VirtualFile[]{virtualFile})
                .hasReadonlyFiles();
    }
}
