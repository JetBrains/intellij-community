package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.ButtonModel;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

public class UnnecessarilyQualifiedStaticUsageInspection extends ExpressionInspection {
    public boolean m_ignoreStaticFieldAccesses = false;
    public boolean m_ignoreStaticMethodCalls = false;
    private final UnnecessarilyQualifiedStaticCallFix fix = new UnnecessarilyQualifiedStaticCallFix();

    public String getDisplayName() {
        return "Unnecessarily qualified static usage";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiElement parent = location.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            return "Unnecessarily qualified static method call '#ref()' #loc";
        } else {
            return "Unnecessarily qualified static field access '#ref' #loc";
        }
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox ignoreFieldAccessesCheckBox = new JCheckBox("Ignore unnecessarily qualified field accesses",
                m_ignoreStaticFieldAccesses);
        final ButtonModel ignoreFieldAccessesModel = ignoreFieldAccessesCheckBox.getModel();
        ignoreFieldAccessesModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_ignoreStaticFieldAccesses = ignoreFieldAccessesModel.isSelected();
            }
        });
        final JCheckBox ignoreMethodCallsCheckBox = new JCheckBox("Ignore unnecessarily qualified method calls",
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
        return new UnnecessarilyQualifiedStaticCallVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarilyQualifiedStaticCallFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary qualifier";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final String newExpression = expression.getReferenceName();
            replaceExpression(project, expression, newExpression);
        }
    }

    private class UnnecessarilyQualifiedStaticCallVisitor extends BaseInspectionVisitor {

        private UnnecessarilyQualifiedStaticCallVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (m_ignoreStaticMethodCalls) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (!isUnnecessarilyQualifiedMethodCall(methodExpression)) {
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
            if (!isUnnecessarilyQualifiedFieldAccess(expression)) {
                return;
            }
            registerError(expression);
        }

        private boolean isUnnecessarilyQualifiedFieldAccess(PsiReferenceExpression expression) {
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField) && !(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMember member = (PsiMember) element;
            if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
            final PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiJavaCodeReferenceElement)) {
                return false;
            }
            final PsiElement qualifierElement =
                    ((PsiReference) qualifierExpression).resolve();
            if (!(qualifierElement instanceof PsiClass)) {
                return false;
            }
            final String referenceName = expression.getReferenceName();
            PsiClass parentClass = ClassUtils.getContainingClass(expression);
            PsiClass containingClass = parentClass;
            while (parentClass != null) {
                containingClass = parentClass;
                final PsiField[] fields = containingClass.getAllFields();
                for (int i = 0; i < fields.length; i++) {
                    final PsiField field = fields[i];
                    final String name = field.getName();
                    if (referenceName.equals(name) && !containingClass.equals(qualifierElement)) {
                        return false;
                    }
                }
                parentClass = ClassUtils.getContainingClass(containingClass);
            }
            PsiMethod containingMethod =
                    (PsiMethod) PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            while (containingMethod != null) {
                final PsiParameterList parameterList = containingMethod.getParameterList();
                final PsiParameter[] parameters = parameterList.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    final PsiParameter parameter = parameters[i];
                    final String name = parameter.getName();
                    if (referenceName.equals(name)) {
                        return false;
                    }
                }
                containingMethod =
                        (PsiMethod) PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class);
            }
            if (!qualifierElement.equals(containingClass)) {
                return false;
            }
            return true;
        }

        private boolean isUnnecessarilyQualifiedMethodCall(PsiReferenceExpression expression) {
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField) && !(element instanceof PsiMethod)) {
                return false;
            }
            final PsiMember member = (PsiMember) element;
            if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
            final PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiJavaCodeReferenceElement)) {
                return false;
            }
            final PsiElement qualifierElement = ((PsiJavaCodeReferenceElement) qualifierExpression).resolve();
            if (!(qualifierElement instanceof PsiClass)) {
                return false;
            }
            final String referenceName = expression.getReferenceName();
            PsiClass parentClass = ClassUtils.getContainingClass(expression);
            PsiClass containingClass = parentClass;
            while (parentClass != null) {
                containingClass = parentClass;
                final PsiMethod[] methods = containingClass.getAllMethods();
                for (int i = 0; i < methods.length; i++) {
                    final PsiMethod method = methods[i];
                    final String name = method.getName();
                    if (referenceName.equals(name) && !containingClass.equals(qualifierElement)) {
                        return false;
                    }
                }
                parentClass = ClassUtils.getContainingClass(containingClass);
            }
            if (!qualifierElement.equals(containingClass)) {
                return false;
            }
            return true;
        }
    }
}