package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class PrivateMemberAccessBetweenOuterAndInnerClassInspection
        extends ClassInspection{
    public String getDisplayName(){
        return "Private member access between outer and inner classes";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    protected String buildErrorString(Object arg){
        return "Access to private member of class '" + arg + "' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new MakePackagePrivateFix(location);
    }

    private static class MakePackagePrivateFix extends InspectionGadgetsFix{
        private String elementName;

        private MakePackagePrivateFix(PsiElement location){
            super();
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression) location;
            final PsiMember member = (PsiMember) reference.resolve();
            assert member != null;
            final String memberName = member.getName();
            final PsiClass containingClass = member.getContainingClass();
            final String containingClassName = containingClass.getName();
            elementName = containingClassName + '.' + memberName;
        }

        public String getName(){
            return "Make '" + elementName + "' package local";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            try{
                final PsiReferenceExpression reference =
                        (PsiReferenceExpression) descriptor.getPsiElement();
                final PsiModifierListOwner member =
                        (PsiModifierListOwner) reference.resolve();
                assert member != null;
                final PsiModifierList modifiers = member.getModifierList();
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
                modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
            } catch(IncorrectOperationException e){
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new PrivateMemberAccessFromInnerClassVisior();
    }

    private static class PrivateMemberAccessFromInnerClassVisior
            extends BaseInspectionVisitor{
        private boolean m_inClass = false;

        public void visitClass(@NotNull PsiClass aClass){
            final boolean wasInClass = m_inClass;
            if(!m_inClass){
                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            final PsiElement element = expression.resolve();
            if(!(element instanceof PsiMethod || element instanceof PsiField)){
                return;
            }
            final PsiMember member = (PsiMember) element;
            if(!member.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            final PsiElement containingClass =
                    ClassUtils.getContainingClass(expression);
            if(containingClass == null){
                return;
            }
            final PsiClass memberClass =
                    ClassUtils.getContainingClass(member);
            if(memberClass == null)
            {
                return;
            }
            if(memberClass.equals(containingClass)){
                return;
            }
            final String memberClassName = memberClass.getName();
            registerError(expression, memberClassName);
        }
    }
}