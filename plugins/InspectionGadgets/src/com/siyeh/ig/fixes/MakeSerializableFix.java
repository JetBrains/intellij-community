package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

public class MakeSerializableFix extends InspectionGadgetsFix {
    private static final Logger s_logger =
            Logger.getInstance("MakeSerializableFix");
    public String getName() {
        return "Make class Serializable";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiClass containingClass = (PsiClass) PsiTreeUtil.getParentOfType(nameElement, PsiClass.class);
        final PsiManager psiManager = containingClass.getManager();
        final PsiElementFactory elementFactory = psiManager.getElementFactory();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiJavaCodeReferenceElement ref = elementFactory.createReferenceElementByFQClassName("java.io.Serializable", scope);
        try{
            containingClass.getImplementsList().add(ref);
        } catch(IncorrectOperationException e){
            s_logger.error(e);
        }
    }
}
