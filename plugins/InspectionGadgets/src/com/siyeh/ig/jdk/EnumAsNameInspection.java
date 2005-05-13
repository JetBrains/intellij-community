package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class EnumAsNameInspection extends BaseInspection {
    private static final String ENUM_STRING = "enum";

    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "EnumAsIdentifier";
    }
    public String getDisplayName() {
        return "Use of 'enum' as identifier";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Use of '#ref' as identifier #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public ProblemDescriptor[] doCheckClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
        if (aClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckMethod(method, mgr, isOnTheFly);
        }

        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (!containingClass.isPhysical()) {
            return super.doCheckField(field, mgr, isOnTheFly);
        }
        if (containingClass instanceof PsiAnonymousClass) {
            return super.doCheckClass(containingClass, mgr, isOnTheFly);
        }

        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EnumAsNameVisitor(this, inspectionManager, onTheFly);
    }

    private static class EnumAsNameVisitor extends BaseInspectionVisitor {

        private EnumAsNameVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final String variableName = variable.getName();
            if (!ENUM_STRING.equals(variableName)) {
                return;
            }
            registerVariableError(variable);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final String name = method.getName();
            if (!ENUM_STRING.equals(name)) {
                return;
            }
            registerMethodError(method);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            //note: no call to super, to avoid drill-down
            final String name = aClass.getName();
            if (!ENUM_STRING.equals(name)) {
                return;
            }
            final PsiTypeParameterList params = aClass.getTypeParameterList();
            if (params != null) {
                params.accept(this);
            }
            registerClassError(aClass);
        }

        public void visitTypeParameter(PsiTypeParameter parameter) {
            super.visitTypeParameter(parameter);
            final String name = parameter.getName();
            if (!ENUM_STRING.equals(name)) {
                return;
            }
            registerTypeParameterError(parameter);
        }
    }

}
