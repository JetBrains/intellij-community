package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class MethodMayBeStaticInspection extends MethodInspection {
    public boolean m_onlyPrivateOrFinal = false;
    public boolean m_ignoreEmptyMethods = true;
    private final MethodMayBeStaticFix fix = new MethodMayBeStaticFix();

    public String getDisplayName() {
        return "Method may be 'static'";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location) {
        return "Method #ref may be 'static' #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MethodMayBeStaticFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make static";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiJavaToken classNameToken = (PsiJavaToken) descriptor.getPsiElement();
            try {
                final PsiMethod innerClass = (PsiMethod) classNameToken.getParent();
                final PsiModifierList modifiers = innerClass.getModifierList();
                modifiers.setModifierProperty(PsiModifier.STATIC, true);
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox ignoreFieldAccessesCheckBox = new JCheckBox("Only check private or final methods",
                m_onlyPrivateOrFinal);
        final ButtonModel ignoreFieldAccessesModel = ignoreFieldAccessesCheckBox.getModel();
        ignoreFieldAccessesModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_onlyPrivateOrFinal = ignoreFieldAccessesModel.isSelected();
            }
        });
        final JCheckBox ignoreEmptyMethodsCheckBox = new JCheckBox("Ignore empty methods",
                m_ignoreEmptyMethods);
        final ButtonModel ignoreEmptyMethodsModel = ignoreEmptyMethodsCheckBox.getModel();
        ignoreEmptyMethodsModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                m_ignoreEmptyMethods = ignoreEmptyMethodsModel.isSelected();
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
        panel.add(ignoreEmptyMethodsCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodCanBeStaticVisitor(this, inspectionManager, onTheFly);
    }

    private class MethodCanBeStaticVisitor extends BaseInspectionVisitor {
        private MethodCanBeStaticVisitor(BaseInspection inspection,
                                         InspectionManager inspectionManager,
                                         boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (method.isConstructor()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (m_ignoreEmptyMethods) {
                final PsiCodeBlock methodBody = method.getBody();
                if (methodBody == null) {
                    return;
                }
                final PsiStatement[] methodStatements = methodBody.getStatements();
                if (methodStatements.length == 0) {
                    return;
                }
            }
            final PsiClass containingClass = ClassUtils.getContainingClass(method);
            final PsiElement scope = containingClass.getScope();
            if (!(scope instanceof PsiJavaFile) &&
                    !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (m_onlyPrivateOrFinal &&
                    !method.hasModifierProperty(PsiModifier.FINAL) &&
                    !method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiMethod[] superMethods = method.findSuperMethods();
            if (superMethods.length > 0) {
                return;
            }
            final OverridingMethodChecker overridingMethodChecker =
                    new OverridingMethodChecker(method);
            if (overridingMethodChecker.hasOverridingMethods()) {
                return;
            }
            final MethodReferenceVisitor visitor = new MethodReferenceVisitor(method);
            method.accept(visitor);
            if (!visitor.areReferencesStaticallyAccessible()) {
                return;
            }
            registerMethodError(method);
        }
    }

    private static class OverridingMethodChecker implements Runnable {
        private PsiMethod m_method;
        private boolean m_hasOverridingMethods = false;

        OverridingMethodChecker(PsiMethod method) {
            super();
            m_method = method;
        }

        public boolean hasOverridingMethods() {
            final ProgressManager progressManager = ProgressManager.getInstance();
            progressManager.runProcess(this, null);
            return m_hasOverridingMethods;
        }

        public void run() {
            final PsiManager manager = m_method.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final GlobalSearchScope resolveScope = m_method.getResolveScope();
            final PsiMethod[] overridingMethods =
                    searchHelper.findOverridingMethods(m_method,
                            resolveScope, true);
            m_hasOverridingMethods = overridingMethods.length > 0;
        }
    }
}