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
    public String getName() {
        return "Make class Cloneable";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
                                                                     throws IncorrectOperationException{
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiClass containingClass = ClassUtils.getContainingClass(nameElement);
        assert containingClass != null;
        final PsiManager psiManager = containingClass.getManager();
        final PsiElementFactory elementFactory = psiManager.getElementFactory();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiJavaCodeReferenceElement ref = elementFactory.createReferenceElementByFQClassName("java.lang.Cloneable", scope);
            final PsiReferenceList implementsList = containingClass.getImplementsList();
            assert implementsList != null;
            implementsList.add(ref);

    }
}
