package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import org.jetbrains.annotations.NotNull;

public class LimitedScopeInnerClassInspection extends ClassInspection {

    private final MoveClassFix fix = new MoveClassFix();
    public String getDisplayName() {
        return "Limited-scope inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Limited-scope inner class #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }
    public BaseInspectionVisitor buildVisitor() {
        return new LimitedScopeInnerClassVisitor();
    }

    private static class LimitedScopeInnerClassVisitor extends BaseInspectionVisitor {
        public void visitClass(@NotNull PsiClass aClass) {
            if (aClass.getParent() instanceof PsiDeclarationStatement) {
                registerClassError(aClass);
            }
        }
    }
}