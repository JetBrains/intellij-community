package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

public class CStyleArrayDeclarationInspection extends ClassInspection {
    private final CStyleArrayDeclarationFix cStyleArrayDeclarationFix = new CStyleArrayDeclarationFix();

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
        return cStyleArrayDeclarationFix;
    }

    private static class CStyleArrayDeclarationFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with Java-style array declaration";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement nameElement = descriptor.getPsiElement();
            final PsiVariable var = (PsiVariable) nameElement.getParent();
            try {
                var.normalizeDeclaration();
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CStyleArrayDeclarationVisitor(this, inspectionManager, onTheFly);
    }

    private static class CStyleArrayDeclarationVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private CStyleArrayDeclarationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitVariable(PsiVariable var) {
            super.visitVariable(var);
            final PsiTypeElement typeElement = var.getTypeElement();
            if (typeElement == null) {
                return; // Could be true for enum constants.
            }
            final PsiType elementType = typeElement.getType();
            final PsiType declared = var.getType();
            if (elementType.equals(declared)) {
                return;
            }
            registerVariableError(var);
        }
    }

}
