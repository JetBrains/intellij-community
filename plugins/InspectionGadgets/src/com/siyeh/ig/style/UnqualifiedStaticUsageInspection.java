package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class UnqualifiedStaticUsageInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticFieldAccesses = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreStaticMethodCalls = false;

    public String getDisplayName() {
        return "Unqualified static usage";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        if (location.getParent() instanceof PsiMethodCallExpression) {
            return "Unqualified static method call '#ref()' #loc";
        } else {
            return "Unqualified static field access '#ref' #loc";
        }
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox ignoreFieldAccessesCheckBox = new JCheckBox("Ignore unqualified field accesses",
                m_ignoreStaticFieldAccesses);
        final ButtonModel ignoreFieldAccessesModel = ignoreFieldAccessesCheckBox.getModel();
        ignoreFieldAccessesModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_ignoreStaticFieldAccesses = ignoreFieldAccessesModel.isSelected();
            }
        });
        final JCheckBox ignoreMethodCallsCheckBox = new JCheckBox("Ignore unqualified method calls",
                m_ignoreStaticMethodCalls);
        final ButtonModel ignoreMethodCallsModel = ignoreMethodCallsCheckBox.getModel();
        ignoreMethodCallsModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_ignoreStaticMethodCalls = ignoreMethodCallsModel.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(ignoreFieldAccessesCheckBox, constraints);
        constraints.gridy = 1;
        panel.add(ignoreMethodCallsCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnqualifiedStaticCallVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        if (location.getParent() instanceof PsiMethodCallExpression) {
            return new UnqualifiedStaticAccessFix(false);
        } else {
            return new UnqualifiedStaticAccessFix(true);
        }
    }

    private static class UnqualifiedStaticAccessFix extends InspectionGadgetsFix {
        private boolean m_fixField;

        UnqualifiedStaticAccessFix(boolean fixField) {
            super();
            m_fixField = fixField;
        }

        public String getName() {
            if (m_fixField) {
                return "Qualify static field access";
            } else {
                return "Qualify static method call";
            }
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiMember member = (PsiMember) expression.resolve();
            final PsiClass containingClass = member.getContainingClass();
            final String className = containingClass.getName();
            final String text = expression.getText();
            replaceExpression(project, expression, className + '.' + text);
        }
    }

    private class UnqualifiedStaticCallVisitor extends BaseInspectionVisitor {
        private UnqualifiedStaticCallVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_ignoreStaticMethodCalls) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (!isUnqualifiedStaticAccess(methodExpression)) {
                return;
            }
            registerError(methodExpression);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (m_ignoreStaticFieldAccesses) {
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            if (!isUnqualifiedStaticAccess(expression)) {
                return;
            }
            registerError(expression);
        }

        private boolean isUnqualifiedStaticAccess(PsiReferenceExpression expression) {
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField) && !(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMember member = (PsiMember) element;
            if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
            final PsiExpression qualifierExpression = expression.getQualifierExpression();
            return qualifierExpression == null;
        }
    }
}