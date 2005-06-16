package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

public class MakeInitializerExplicitFix extends InspectionGadgetsFix{
    public String getName(){
        return "Make initialization explicit";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException{
        final PsiElement fieldName = descriptor.getPsiElement();
        final PsiField field = (PsiField) fieldName.getParent();
        final PsiManager psiManager = PsiManager.getInstance(project);
        final PsiElementFactory factory =
                psiManager.getElementFactory();
        assert field != null;
        final PsiModifierList modifiers = field.getModifierList();
        final PsiType type = field.getType();
        final String name = field.getName();
        final PsiClass containingClass = field.getContainingClass();
        final String newFieldText =
                modifiers.getText() + ' ' + type.getPresentableText() +
                        ' ' + name +
                        '=' + getDefaultValue(type) + ';';
        final PsiField newField = factory
                .createFieldFromText(newFieldText, containingClass);
        final CodeStyleManager styleManager =
                psiManager.getCodeStyleManager();
        final PsiElement replacedField = field.replace(newField);
        styleManager.reformat(replacedField);
    }

    private String getDefaultValue(PsiType type){
        if(PsiType.INT.equals(type)){
            return "0";
        } else if(PsiType.LONG.equals(type)){
            return "0L";
        } else if(PsiType.DOUBLE.equals(type)){
            return "0.0";
        } else if(PsiType.FLOAT.equals(type)){
            return "0.0F";
        } else if(PsiType.SHORT.equals(type)){
            return "(short)0";
        } else if(PsiType.BYTE.equals(type)){
            return "(byte)0";
        } else if(PsiType.BOOLEAN.equals(type)){
            return "false";
        } else if(PsiType.CHAR.equals(type)){
            return "(char)0";
        }
        return "null";
    }
}
