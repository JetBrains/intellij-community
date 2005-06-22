package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import org.jetbrains.annotations.NotNull;

public class MultipleTopLevelClassesInFileInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getDisplayName() {
        return "Multiple top level classes in single file";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Multiple top level classes in file";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MultipleTopLevelClassesInFileVisitor();
    }

    private static class MultipleTopLevelClassesInFileVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if(file == null)
            {
                return;
            }
            int numClasses=0;
            final PsiElement[] children = file.getChildren();
            for(final PsiElement child : children){
                if(child instanceof PsiClass){
                    numClasses++;
                }
            }
            if (numClasses <= 1) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
