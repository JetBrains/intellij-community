package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class UnnecessaryInterfaceModifierInspection extends BaseInspection {

    public String getDisplayName() {
        return "Unnecessary interface modifier";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
        if (!aClass.isPhysical()) {
            return super.checkClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);

        return visitor.getErrors();
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
        if (!method.isPhysical()) {
            return super.checkMethod(method, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] checkField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        if (!field.isPhysical()) {
            return super.checkField(field, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifierList;
        if (location instanceof PsiModifierList) {
            modifierList = (PsiModifierList) location;
        } else {
            modifierList = (PsiModifierList) location.getParent();
        }
        final PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiClass) {
            return "Modifier '#ref' is redundant for interfaces #loc";
        } else if (parent instanceof PsiMethod) {
            if (modifierList.getChildren().length > 1) {
                return "Modifiers '#ref' are redundant for interface methods #loc";
            } else {
                return "Modifier '#ref' is redundant for interface methods #loc";
            }
        } else {
            if (modifierList.getChildren().length > 1) {
                return "Modifiers '#ref' are redundant for interface fields #loc";
            } else {
                return "Modifier '#ref' is redundant for interface fields #loc";
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryInterfaceModifierVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryInterfaceModifersFix(location);
    }

    private static class UnnecessaryInterfaceModifersFix extends InspectionGadgetsFix {
        private final String m_name;

        private UnnecessaryInterfaceModifersFix(PsiElement fieldModifiers) {
            super();
            m_name = "Remove '" + fieldModifiers.getText() + '\'';
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            try {
                final PsiElement element = descriptor.getPsiElement();
                final PsiModifierList modifierList;
                if (element instanceof PsiModifierList) {
                    modifierList = (PsiModifierList) element;
                } else {
                    modifierList = (PsiModifierList) element.getParent();
                }
                modifierList.setModifierProperty(PsiModifier.STATIC, false);
                modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
                modifierList.setModifierProperty(PsiModifier.FINAL, false);
                if (!(modifierList.getParent() instanceof PsiClass)) {
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                }
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {

        private UnnecessaryInterfaceModifierVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (aClass.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                if (modifiers == null) {
                    return;
                }
                final PsiElement[] children = modifiers.getChildren();
                for (int i = 0; i < children.length; i++) {
                    final PsiElement child = children[i];
                    final String text = child.getText();
                    if (PsiModifier.ABSTRACT.equals(text)) {
                        registerError(child);
                    }
                }
            }
            final PsiClass parent = ClassUtils.getContainingClass(aClass);
            if (parent != null && parent.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                if (modifiers == null) {
                    return;
                }
                final PsiElement[] children = modifiers.getChildren();
                for (int i = 0; i < children.length; i++) {
                    final PsiElement child = children[i];
                    final String text = child.getText();
                    if (PsiModifier.PUBLIC.equals(text) ||
                            PsiModifier.STATIC.equals(text)) {
                        registerError(child);
                    }
                }
            }
        }

        public void visitField(PsiField field) {
            // don't call super, to keep this from drilling in
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = field.getModifierList();
            if (modifiers == null) {
                return;
            }
            // the only modifiers an interface field can have (public static final)
            // are unnecessary.
            final int modifiersLength = modifiers.getTextLength();
            if (modifiersLength != 0) {
                registerError(modifiers);
            }
        }

        public void visitMethod(PsiMethod method) {
            // don't call super, to keep this from drilling in
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (!aClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = method.getModifierList();
            // the only modifiers an interface field can have (public static final)
            // are unnecessary.
            final int modifiersLength = modifiers.getTextLength();
            if (modifiersLength != 0) {
                registerError(modifiers);
            }
        }

    }
}
