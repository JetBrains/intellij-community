package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.UtilityClassUtil;

public class UtilityClassWithPublicConstructorInspection extends ClassInspection {

    public String getDisplayName() {
        return "Utility class with public constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref has only static members, and a public constructor #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiClass psiClass = (PsiClass) location.getParent();
        if (psiClass.getConstructors().length > 1) {
            return new UtilityClassWithPublicConstructorFix(true);
        } else {
            return new UtilityClassWithPublicConstructorFix(false);
        }
    }

    private static class UtilityClassWithPublicConstructorFix extends InspectionGadgetsFix {
        private final boolean m_multipleConstructors;

        UtilityClassWithPublicConstructorFix(boolean multipleConstructors) {
            super();
            m_multipleConstructors = multipleConstructors;
        }

        public String getName() {
            if (m_multipleConstructors) {
                return "Make constructors private";
            } else {
                return "Make constructor private";
            }
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            try {
                final PsiElement classNameIdentifer = descriptor.getPsiElement();
                final PsiClass psiClass = (PsiClass) classNameIdentifer.getParent();
                final PsiMethod[] constructors = psiClass.getConstructors();
                for (int i = 0; i < constructors.length; i++) {
                    final PsiModifierList modifierList = constructors[i].getModifierList();
                    modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
                }
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticClassWithPublicConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class StaticClassWithPublicConstructorVisitor extends BaseInspectionVisitor {
        private StaticClassWithPublicConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }

            if (!hasPublicConstructor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

    }

    private static boolean hasPublicConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            if (method.isConstructor() && method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return true;
            }
        }
        return false;
    }
}
