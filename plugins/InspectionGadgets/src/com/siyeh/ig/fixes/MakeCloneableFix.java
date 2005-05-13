package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class MakeCloneableFix extends InspectionGadgetsFix {
    private static final Logger s_logger =
            Logger.getInstance("MakeCloneableFix");
    public String getName() {
        return "Make class Cloneable";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiClass containingClass = ClassUtils.getContainingClass(nameElement);
        final PsiManager psiManager = containingClass.getManager();
        final PsiElementFactory elementFactory = psiManager.getElementFactory();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiJavaCodeReferenceElement ref = elementFactory.createReferenceElementByFQClassName("java.lang.Cloneable", scope);
        try{
            containingClass.getImplementsList().add(ref);
        } catch(IncorrectOperationException e){
            s_logger.error(e);
        }
    }
}
