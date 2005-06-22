package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class ParameterNamingConventionInspection extends ConventionInspection {
    private static final int DEFAULT_MIN_LENGTH = 1;
    private static final int DEFAULT_MAX_LENGTH = 20;
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "MethodParameterNamingConvention";
    }
    public String getDisplayName() {
        return "Method parameter naming convention";
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
        final PsiParameter param = (PsiParameter) location.getParent();
        assert param != null;
        final String paramName = param.getName();
        if (paramName.length() < getMinLength()) {
            return "Parameter name '#ref' is too short #loc";
        } else if (paramName.length() > getMaxLength()) {
            return "Parameter name '#ref' is too long #loc";
        } else {
            return "Parameter name '#ref' doesn't match regex '" + getRegex() + "' #loc";
        }
    }

    protected String getDefaultRegex() {
        return "[a-z][A-Za-z]*";
    }

    protected int getDefaultMinLength() {
        return DEFAULT_MIN_LENGTH;
    }

    protected int getDefaultMaxLength() {
        return DEFAULT_MAX_LENGTH;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NamingConventionsVisitor();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return super.doCheckMethod(method, manager, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckMethod(method, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(manager, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor {

        public void visitParameter(@NotNull PsiParameter variable) {
            if (variable.getDeclarationScope() instanceof PsiCatchSection) {
                return;
            }
            final String name = variable.getName();
            if (name == null) {
                return;
            }
            if (isValid(name)) {
                return;
            }
            registerVariableError(variable);
        }

    }
}
