package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ClassWithoutConstructorInspection extends ClassInspection{
    private final ClassWithoutConstructorFix fix = new ClassWithoutConstructorFix();

    public String getDisplayName(){
        return "Class without constructor";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref has no constructor #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ClassWithoutConstructorFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Create empty constructor";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement classIdentifier = descriptor.getPsiElement();
            final PsiClass psiClass = (PsiClass) classIdentifier.getParent();
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiMethod constructor = factory.createConstructor();
            final PsiModifierList modifierList = constructor.getModifierList();
            assert psiClass != null;
            if(psiClass.hasModifierProperty(PsiModifier.PRIVATE)){
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            } else if(psiClass.hasModifierProperty(PsiModifier.PROTECTED)){
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
            } else if(psiClass.hasModifierProperty(PsiModifier.ABSTRACT)){
                modifierList
                        .setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList
                        .setModifierProperty(PsiModifier.PROTECTED, true);
            } else if(!psiClass.hasModifierProperty(PsiModifier.PUBLIC)){
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
            }

            psiClass.add(constructor);
            final CodeStyleManager styleManager = psiManager
                    .getCodeStyleManager();
            styleManager.reformat(constructor);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ClassWithoutConstructorVisitor();
    }

    private static class ClassWithoutConstructorVisitor
            extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            if(aClass.isInterface() || aClass.isEnum() ||
                    aClass.isAnnotationType()){
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            if(classHasConstructor(aClass)){
                return;
            }
            registerClassError(aClass);
        }

        private static boolean classHasConstructor(PsiClass aClass){
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
