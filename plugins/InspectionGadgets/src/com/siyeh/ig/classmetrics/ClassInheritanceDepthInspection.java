package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;

import java.util.HashSet;
import java.util.Set;

public class ClassInheritanceDepthInspection
        extends ClassMetricInspection {
    public String getID(){
        return "ClassTooDeepInInheritanceTree";
    }
    private static final int CLASS_INHERITANCE_LIMIT = 2;

    public String getDisplayName() {
        return "Class too deep in inheritance tree";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return CLASS_INHERITANCE_LIMIT;
    }

    protected String getConfigurationLabel() {
        return "Inheritance depth limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final int count = getInheritanceDepth(aClass, new HashSet());
        return "#ref is too deep in inheritance tree (inheritance depth = " + count + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassNestingLevel(this, inspectionManager, onTheFly);
    }

    private class ClassNestingLevel extends BaseInspectionVisitor {
        private ClassNestingLevel(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (aClass.isEnum()) {
                return;
            }

            // note: no call to super

            final int inheritanceDepth = getInheritanceDepth(aClass, new HashSet());
            if (inheritanceDepth <= getLimit()) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private int getInheritanceDepth(PsiClass aClass, Set visited) {
        if (visited.contains(aClass)) {
            return 0;
        }
        visited.add(aClass);
        final PsiClass[] supers = aClass.getSupers();
        if (supers == null || supers.length == 0) {
            return 0;
        }
        int maxAncestorDepth = 0;
        for (int i = 0; i < supers.length; i++) {
            final int ancestorDepth = getInheritanceDepth(supers[i], visited);
            if (ancestorDepth > maxAncestorDepth) {
                maxAncestorDepth = ancestorDepth;
            }
        }
        return maxAncestorDepth + 1;
    }

}
