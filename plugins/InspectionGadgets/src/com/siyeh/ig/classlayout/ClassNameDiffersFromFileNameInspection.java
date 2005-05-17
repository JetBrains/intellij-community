package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class ClassNameDiffersFromFileNameInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class name differs from file name";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class name #ref differs from file name #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        final PsiClass aClass = (PsiClass) location.getParent();
        final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
        final String fileName = file.getName();
        final int prefixIndex = fileName.indexOf((int) '.');
        final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
        final PsiClass[] classes = file.getClasses();
        for(PsiClass psiClass : classes){
            final String className = psiClass.getName();
            if(className.equals(filenameWithoutPrefix))
            {
                return null;
            }
        }
        return new RenameFix(filenameWithoutPrefix);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new classNameDiffersFromFileName();
    }

    private static class classNameDiffersFromFileName extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            final String className = aClass.getName();
            if (className == null) {
                return;
            }
            final String fileName = file.getName();
            if (fileName == null) {
                return;
            }
            final int prefixIndex = fileName.indexOf((int) '.');
            final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
            if (className.equals(filenameWithoutPrefix)) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
