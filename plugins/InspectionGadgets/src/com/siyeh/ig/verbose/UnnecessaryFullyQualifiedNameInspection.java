package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class UnnecessaryFullyQualifiedNameInspection extends ClassInspection {
    public boolean m_ignoreJavaDocs = false;
    private final UnnecessaryFullyQualifiedNameFix fix = new UnnecessaryFullyQualifiedNameFix();

    public String getDisplayName() {
        return "Unnecessary fully qualified name";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Fully qualified name #ref is unnecessary, and can be replace with an import #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore fully qualified names in javadoc comments",
                this, "m_ignoreJavaDocs");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryFullyQualifiedNameVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        if (m_ignoreJavaDocs && PsiTreeUtil.getParentOfType(location, PsiDocTag.class) != null) {
            return null;
        }
        return fix;
    }

    private static class UnnecessaryFullyQualifiedNameFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with import";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            try {
                final PsiManager mgr = PsiManager.getInstance(project);
                final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement) descriptor.getPsiElement();
                final CodeStyleManager styleManager = mgr.getCodeStyleManager();
                styleManager.shortenClassReferences(reference);
            } catch (IncorrectOperationException e) {
                final Class thisClass = getClass();
                final String className = thisClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    private class UnnecessaryFullyQualifiedNameVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private UnnecessaryFullyQualifiedNameVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String expressionText = expression.getText();
            final String text = expressionText;
            if (text.indexOf((int) '.') < 0) {
                return;
            }
            final PsiElement psiElement = expression.resolve();
            if (!(psiElement instanceof PsiClass)) {
                if (qualifier != null) {
                    qualifier.accept(this);
                }
                return;
            }
            final PsiReferenceParameterList typeParameters = expression.getParameterList();
            if(typeParameters!=null)
            {
                typeParameters.accept(this);
            }
            final PsiClass aClass = (PsiClass) psiElement;
            final PsiClass outerClass = ClassUtils.getOutermostContainingClass(aClass);
            final String fqName = outerClass.getQualifiedName();
            if (!expressionText.startsWith(fqName)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) expression.getContainingFile();

            if (!ImportUtils.nameCanBeImported(text, file)) {
                return;
            }
            registerError(expression);
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement element) {
            final String text = element.getText();
            if (text.indexOf((int) '.') < 0) {
                return;
            }
            if (m_ignoreJavaDocs && PsiTreeUtil.getParentOfType(element, PsiDocTag.class) != null) {
                return;
            }
            final PsiElement psiElement = element.resolve();
            if (!(psiElement instanceof PsiClass)) {
                return;
            }
            final PsiReferenceParameterList typeParameters = element.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
            final PsiClass aClass = (PsiClass) psiElement;
            final PsiClass outerClass = ClassUtils.getOutermostContainingClass(aClass);
            final String fqName = outerClass.getQualifiedName();
            if (!element.getText().startsWith(fqName)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) element.getContainingFile();
            if (!ImportUtils.nameCanBeImported(text, file)) {
                return;
            }
            registerError(element);
        }

    }

}
