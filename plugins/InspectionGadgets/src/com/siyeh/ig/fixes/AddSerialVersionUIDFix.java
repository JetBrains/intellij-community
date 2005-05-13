package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

public class AddSerialVersionUIDFix extends InspectionGadgetsFix {
    private static final Logger s_logger =
            Logger.getInstance("MakeSerializableFix");
    public String getName() {
        return "Add serialVersionUIDField";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor){
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement classIdentifier = descriptor.getPsiElement();
        final PsiClass aClass = (PsiClass) classIdentifier.getParent();
        try{
            final PsiManager psiManager = aClass.getManager();
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final long serialVersionUID =
                    SerialVersionUIDBuilder.computeDefaultSUID(aClass);
            final PsiField field =
                    elementFactory.createFieldFromText("private static final long serialVersionUID = " +
                    serialVersionUID + "L;", aClass);
            aClass.add(field);
        } catch(IncorrectOperationException e){
            s_logger.error(e);
        }
    }

}
