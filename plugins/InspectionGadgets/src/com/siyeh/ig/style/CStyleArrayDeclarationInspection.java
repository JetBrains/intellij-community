package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class CStyleArrayDeclarationInspection extends ClassInspection {
    private final CStyleArrayDeclarationFix fix = new CStyleArrayDeclarationFix();

    public String getDisplayName() {
        return "C-style array declaration";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "C-style array declaration #ref #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class CStyleArrayDeclarationFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with Java-style array declaration";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement nameElement = descriptor.getPsiElement();
            final PsiVariable var = (PsiVariable) nameElement.getParent();
                var.normalizeDeclaration();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CStyleArrayDeclarationVisitor();
    }

    private static class CStyleArrayDeclarationVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        public void visitClass(@NotNull PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitVariable(@NotNull PsiVariable var) {
            super.visitVariable(var);
            final PsiType declaredType = var.getType();
            if(declaredType.getArrayDimensions()==0)
            {
                return;
            }
            final PsiTypeElement typeElement = var.getTypeElement();
            if (typeElement == null) {
                return; // Could be true for enum constants.
            }
            final PsiType elementType = typeElement.getType();
            if (elementType.equals(declaredType)) {
                return;
            }
            registerVariableError(var);
        }
    }

}
