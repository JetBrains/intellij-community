package com.siyeh.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;

public class AddSerialVersionUIDFix extends InspectionGadgetsFix {
    public String getName() {
        return "Add serialVersionUIDField";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiElement classIdentifier = descriptor.getPsiElement();
        final PsiElement aClass = classIdentifier.getParent();
        try {
            final PsiManager psiManager = aClass.getManager();
            final PsiElementFactory elementFactory = psiManager.getElementFactory();
            final long serialVersionUID = 1;
            final PsiField field = elementFactory.createFieldFromText("private static final long serialVersionUID = "+ serialVersionUID+"L;", aClass);
            aClass.add(field);
        } catch (IncorrectOperationException e) {
            e.printStackTrace();
        }
    }
}
