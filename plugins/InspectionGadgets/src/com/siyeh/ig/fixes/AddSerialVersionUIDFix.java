package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

public class AddSerialVersionUIDFix extends InspectionGadgetsFix{

    public String getName(){
        return "Add serialVersionUIDField";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException{
        final PsiElement classIdentifier = descriptor.getPsiElement();
        final PsiClass aClass = (PsiClass) classIdentifier.getParent();
        final PsiManager psiManager = aClass.getManager();
        final PsiElementFactory elementFactory =
                psiManager.getElementFactory();
        final long serialVersionUID =
                SerialVersionUIDBuilder.computeDefaultSUID(aClass);
        final PsiField field =
                elementFactory.createFieldFromText("private static final long serialVersionUID = " +
                        serialVersionUID + "L;", aClass);
        aClass.add(field);
    }
}
