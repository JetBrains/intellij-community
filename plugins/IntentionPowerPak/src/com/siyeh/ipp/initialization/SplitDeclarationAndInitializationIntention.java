package com.siyeh.ipp.initialization;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class SplitDeclarationAndInitializationIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new SplitDeclarationAndInitializationPredicate();
    }

    public String getText(){
        return "Split into declaration and initialization";
    }

    public String getFamilyName(){
        return "Split Declaration And Initialization";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiField field = (PsiField) element;
        field.normalizeDeclaration();
        final PsiExpression initializer = field.getInitializer();
        final String initializerText = initializer.getText();
        final PsiManager psiManager = field.getManager();
        final PsiElementFactory elementFactory = psiManager.getElementFactory();
        PsiClassInitializer classInitializer =
                elementFactory.createClassInitializer();
        final PsiClass containingClass = field.getContainingClass();
        classInitializer = (PsiClassInitializer) containingClass.addAfter(classInitializer,
                                                                          field);
        final PsiCodeBlock body = classInitializer.getBody();
        final String initializationStatementText =
                field.getName() + " = " + initializerText + ';';
        final PsiExpressionStatement statement =
                (PsiExpressionStatement) elementFactory.createStatementFromText(
                        initializationStatementText, body);
        body.add(statement);
        if(field.hasModifierProperty(PsiModifier.STATIC)){
            final PsiModifierList modifierList =
                    classInitializer.getModifierList();
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
        initializer.delete();
        final CodeStyleManager codeStyleManager =
                psiManager.getCodeStyleManager();
        codeStyleManager.reformat(field);
        codeStyleManager.reformat(classInitializer);
    }
}
