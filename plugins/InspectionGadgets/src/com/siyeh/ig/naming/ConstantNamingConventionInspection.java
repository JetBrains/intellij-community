package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class ConstantNamingConventionInspection extends ConventionInspection {
    private static final int DEFAULT_MIN_LENGTH = 5;
    private static final int DEFAULT_MAX_LENGTH = 32;
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Constant naming convention";
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
        final PsiField field = (PsiField) location.getParent();
        final String fieldName = field.getName();
        if (fieldName.length() < getMinLength()) {
            return "Constant name '#ref' is too short #loc";
        } else if (fieldName.length() > getMaxLength()) {
            return "Constant name '#ref' is too long #loc";
        }
        return "Constant '#ref' doesn't match regex '" + getRegex() + "' #loc";
    }

    protected String getDefaultRegex() {
        return "[A-Z_]*";
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

    public ProblemDescriptor[] doCheckField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }

        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor {
        private NamingConventionsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                    !field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final String name = field.getName();
            if (name == null) {
                return;
            }
            final PsiType type = field.getType();
            if (type == null) {
                return;
            }
            if (!ClassUtils.isImmutable(type)) {
                return;
            }
            if (isValid(name)) {
                return;
            }
            registerFieldError(field);
        }

    }
}
