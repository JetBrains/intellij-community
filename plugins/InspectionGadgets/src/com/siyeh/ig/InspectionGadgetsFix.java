package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public abstract class InspectionGadgetsFix implements LocalQuickFix {
    protected void deleteElement(PsiElement element) {
        try {
            element.delete();
        } catch (IncorrectOperationException e) {
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected void replaceExpression(Project project, PsiExpression expression, String newExpression) {
        try {
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiExpression newExp = factory.createExpressionFromText(newExpression, null);
            final PsiElement replacementExp = expression.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch (IncorrectOperationException e) {
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected void replaceStatement(Project project, PsiStatement statement, String newStatement) {
        try {
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiStatement newExp = factory.createStatementFromText(newStatement, null);
            final PsiElement replacementExp = statement.replace(newExp);
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(replacementExp);
        } catch (IncorrectOperationException e) {
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }
}
