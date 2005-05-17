package com.siyeh.ig.classmetrics;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class ClassNestingDepthInspection
        extends ClassMetricInspection {
    private static final int CLASS_NESTING_LIMIT = 1;

    public String getID(){
        return "InnerClassTooDeeplyNested";
    }
    public String getDisplayName() {
        return "Inner class too deeply nested";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return CLASS_NESTING_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Nesting limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = getNestingLevel(aClass);
        return "#ref is too deeply nested (nesting level = " + count + ") #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassNestingLevel();
    }

    private class ClassNestingLevel extends BaseInspectionVisitor {
     
        public void visitClass(@NotNull PsiClass aClass) {
            // note: no call to super

            final int nestingLevel = getNestingLevel(aClass);
            if (nestingLevel <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private static int getNestingLevel(PsiClass aClass) {
        PsiElement ancestor = aClass.getParent();
        int nestingLevel = 0;
        while (ancestor != null) {
            if (ancestor instanceof PsiClass) {
                nestingLevel++;
            }
            ancestor = ancestor.getParent();
        }
        return nestingLevel;
    }

}
