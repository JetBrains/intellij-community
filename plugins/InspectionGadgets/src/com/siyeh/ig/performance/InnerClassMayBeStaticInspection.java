package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class InnerClassMayBeStaticInspection extends ClassInspection {
    private final InnerClassMayBeStaticFix fix = new InnerClassMayBeStaticFix();

    public String getDisplayName() {
        return "Inner class may be 'static'";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Inner class #ref may be 'static' #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make static";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiJavaToken classNameToken = (PsiJavaToken) descriptor.getPsiElement();
            try {
                final PsiClass innerClass = (PsiClass) classNameToken.getParent();
                final PsiManager manager = innerClass.getManager();
                final PsiSearchHelper searchHelper = manager.getSearchHelper();
                final SearchScope useScope = innerClass.getUseScope();
                final PsiReference[] references = searchHelper.findReferences(innerClass, useScope, false);
                for(final PsiReference reference : references){
                    final PsiElement element = reference.getElement();
                    final PsiElement parent = element.getParent();
                    if(parent instanceof PsiNewExpression){
                        final PsiNewExpression newExpression = (PsiNewExpression) parent;
                        final PsiExpression qualifier = newExpression.getQualifier();
                        if(qualifier != null){
                            qualifier.delete();
                        }
                    }
                }
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

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassCanBeStaticVisitor();
    }

    private static class InnerClassCanBeStaticVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            final PsiClass[] innerClasses = aClass.getInnerClasses();
            for(final PsiClass innerClass : innerClasses){
                if(!innerClass.hasModifierProperty(PsiModifier.STATIC)){
                    final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(innerClass);
                    innerClass.accept(visitor);
                    if(visitor.areReferenceStaticallyAccessible()){
                        registerClassError(innerClass);
                    }
                }
            }
        }
    }
}