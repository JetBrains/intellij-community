package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

public class InterfaceNamingConventionInspection extends ConventionInspection {
    private static final int DEFAULT_MIN_LENGTH = 8;
    private static final int DEFAULT_MAX_LENGTH = 64;
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Interface naming convention";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final String className = aClass.getName();
        if (className.length() < getMinLength()) {
            return "Interface name '#ref' is too short #loc";
        } else if (className.length() > getMaxLength()) {
            return "Interface name '#ref' is too long #loc";
        }
        return "Interface name '#ref' doesn't match regex '" + getRegex() + "' #loc";
    }

    protected String getDefaultRegex() {
        return "[A-Z][A-Za-z]*";
    }

    protected int getDefaultMinLength() {
        return DEFAULT_MIN_LENGTH;
    }

    protected int getDefaultMaxLength() {
        return DEFAULT_MAX_LENGTH;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NamingConventionsVisitor(this, inspectionManager, onTheFly);
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {

        if (!aClass.isPhysical()) {
            return super.checkClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);

        return visitor.getErrors();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor {
        private NamingConventionsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (!aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            final String name = aClass.getName();
            if (name == null) {
                return;
            }
            if (isValid(name)) {
                return;
            }
            registerClassError(aClass);
        }

    }
}
