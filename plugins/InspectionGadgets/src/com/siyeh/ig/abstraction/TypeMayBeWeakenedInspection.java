package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeMayBeWeakenedInspection extends VariableInspection {

    public String getDisplayName() {
        return "Type may be weakened";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiClass aClass = (PsiClass) infos[0];
        return "Type of variable <code>#ref</code> may be weakened to '" +
                aClass.getQualifiedName() + "'";
    }


    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new TypeMayBeWeakenedFix();
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Weaken type";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiVariable variable =
                    (PsiVariable)element.getParent();
            final PsiTypeElement typeElement =
                    variable.getTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiClassType type =
                    TypeUtils.calculateWeakestTypeNecessary(variable);
            if (type == null) {
                return;
            }
            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(type);
            typeElement.replace(newTypeElement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }
    
    private static class TypeMayBeWeakenedVisitor
            extends BaseInspectionVisitor {

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiClassType weakestType =
                    TypeUtils.calculateWeakestTypeNecessary(variable);
            if (weakestType == null) {
                return;
            }
            final PsiType variableType = variable.getType();
            final String weakestTypeCanonicalText =
                    weakestType.getInternalCanonicalText();
            final String variableTypeCanonicalText =
                    variableType.getInternalCanonicalText();
            if (weakestTypeCanonicalText.equals(variableTypeCanonicalText)) {
                return;
            }
            final PsiClass weakestClass = weakestType.resolve();
            if (weakestClass == null) {
                return;
            }
            registerVariableError(variable, weakestClass);
        }

    }
}