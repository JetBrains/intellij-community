package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

public class ClassWithoutConstructorInspection extends ClassInspection {
    private final ClassWithoutConstructorFix fix = new ClassWithoutConstructorFix();

    public String getDisplayName() {
        return "Class without constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref has no constructor #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ClassWithoutConstructorFix extends InspectionGadgetsFix {
        public String getName() {
            return "Create empty constructor";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            try {
                final PsiElement classIdentifier = descriptor.getPsiElement();
                final PsiClass psiClass = (PsiClass) classIdentifier.getParent();
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory factory = psiManager.getElementFactory();
                final PsiMethod constructor = factory.createConstructor();
                final PsiModifierList modifierList = constructor.getModifierList();
                if(psiClass.hasModifierProperty(PsiModifier.PRIVATE))
                {
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                    modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
                }
                else if(psiClass.hasModifierProperty(PsiModifier.PROTECTED))
                {
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                    modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
                } else if(psiClass.hasModifierProperty(PsiModifier.ABSTRACT)){
                    modifierList
                            .setModifierProperty(PsiModifier.PUBLIC, false);
                    modifierList
                            .setModifierProperty(PsiModifier.PROTECTED, true);
                }
                else if(!psiClass.hasModifierProperty(PsiModifier.PUBLIC))
                {
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                }

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassWithoutConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassWithoutConstructorVisitor extends BaseInspectionVisitor {
        private ClassWithoutConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass.getNameIdentifier() == null) {
                return; //a very hacky test for anonymous classes
            }
            if (classHasConstructor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean classHasConstructor(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                if(method.isConstructor()){
                    return true;
                }
            }
            return false;
        }
    }

}
