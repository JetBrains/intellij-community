package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;

public class UnnecessarySemicolonInspection extends ClassInspection {
    private final UnnecessarySemicolonFix fix = new UnnecessarySemicolonFix();

    public String getDisplayName() {
        return "Unnecessary semicolon";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary semicolon #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessarySemicolonVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarySemicolonFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary semicolon";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement semicolonElement = descriptor.getPsiElement();
            deleteElement(semicolonElement);
        }

    }

    private static class UnnecessarySemicolonVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private UnnecessarySemicolonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {
                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
            PsiElement sibling = aClass.getNextSibling();
            while (sibling != null) {
                if (sibling instanceof PsiJavaToken &&
                        ((PsiJavaToken) sibling).getTokenType().equals(JavaTokenType.SEMICOLON)) {
                    registerError(sibling);
                }
                sibling = sibling.getNextSibling();
            }
        }

        public void visitJavaToken(PsiJavaToken token) {
            super.visitJavaToken(token);
            final IElementType tokenType = token.getTokenType();
            if (!tokenType.equals(JavaTokenType.SEMICOLON)) {
                return;
            }
            final PsiElement parent = token.getParent();
            if (!(parent instanceof PsiClass) && !(parent instanceof PsiJavaFile)) {
                return;
            }
            if (parent instanceof PsiClass && ((PsiClass) parent).isEnum()) {
                //A very hacky way of saying that semicolons after
                //enum constants are not unnecessary.
                PsiElement prevSibling = token.getPrevSibling();
                while (prevSibling != null && prevSibling instanceof PsiWhiteSpace) {
                    prevSibling = prevSibling.getPrevSibling();
                }
                if (prevSibling instanceof PsiEnumConstant) {
                    return;
                }
            }
            registerError(token);
        }

        public void visitEmptyStatement(PsiEmptyStatement statement) {
            super.visitEmptyStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiCodeBlock) {
                registerError(statement);
            }
        }
    }
}
