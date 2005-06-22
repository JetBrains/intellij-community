package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class UtilityClassWithPublicConstructorInspection
        extends ClassInspection{
    public String getDisplayName(){
        return "Utility class with public constructor";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Class #ref has only static members, and a public constructor #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        final PsiClass psiClass = (PsiClass) location.getParent();
        assert psiClass != null;
        if(psiClass.getConstructors().length > 1){
            return new UtilityClassWithPublicConstructorFix(true);
        } else{
            return new UtilityClassWithPublicConstructorFix(false);
        }
    }

    private static class UtilityClassWithPublicConstructorFix
            extends InspectionGadgetsFix{
        private final boolean m_multipleConstructors;

        UtilityClassWithPublicConstructorFix(boolean multipleConstructors){
            super();
            m_multipleConstructors = multipleConstructors;
        }

        public String getName(){
            if(m_multipleConstructors){
                return "Make constructors private";
            } else{
                return "Make constructor private";
            }
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement classNameIdentifer = descriptor.getPsiElement();
            final PsiClass psiClass = (PsiClass) classNameIdentifer.getParent();
            assert psiClass != null;
            final PsiMethod[] constructors = psiClass.getConstructors();
            for(PsiMethod constructor : constructors){
                final PsiModifierList modifierList = constructor
                        .getModifierList();
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new StaticClassWithPublicConstructorVisitor();
    }

    private static class StaticClassWithPublicConstructorVisitor
            extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so that it doesn't drill down to inner classes
            if(!UtilityClassUtil.isUtilityClass(aClass)){
                return;
            }

            if(!hasPublicConstructor(aClass)){
                return;
            }
            registerClassError(aClass);
        }
    }

    private static boolean hasPublicConstructor(PsiClass aClass){
        final PsiMethod[] methods = aClass.getMethods();
        for(final PsiMethod method : methods){
            if(method.isConstructor() && method
                    .hasModifierProperty(PsiModifier.PUBLIC)){
                return true;
            }
        }
        return false;
    }
}
