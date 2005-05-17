package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class UtilityClassWithoutPrivateConstructorInspection extends ClassInspection {
    private final UtilityClassWithoutPrivateConstructorFix fix = new UtilityClassWithoutPrivateConstructorFix();

    public String getDisplayName() {
        return "Utility class without private constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref has only 'static' members, and lacks a 'private' constructor #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UtilityClassWithoutPrivateConstructorFix extends InspectionGadgetsFix {
        public String getName() {
            return "Create empty private constructor";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            try {
                final PsiElement classNameIdentifier = descriptor.getPsiElement();
                final PsiClass psiClass = (PsiClass) classNameIdentifier.getParent();
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory factory = psiManager.getElementFactory();
                final PsiMethod constructor = factory.createConstructor();
                final PsiModifierList modifierList = constructor.getModifierList();
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
                psiClass.add(constructor);
                final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
                styleManager.reformat(constructor);
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticClassWithoutPrivateConstructorVisitor();
    }

    private static class StaticClassWithoutPrivateConstructorVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }

            if(aClass.hasModifierProperty(PsiModifier.ABSTRACT))
            {
                return;
            }
            if (hasPrivateConstructor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }

    private static boolean hasPrivateConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for(final PsiMethod method : methods){
            if(method.isConstructor() && method
                    .hasModifierProperty(PsiModifier.PRIVATE)){
                return true;
            }
        }
        return false;
    }

}
