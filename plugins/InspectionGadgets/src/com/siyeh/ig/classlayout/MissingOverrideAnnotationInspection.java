package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

public class MissingOverrideAnnotationInspection extends MethodInspection{
    private final MissingOverrideAnnotationFix fix = new MissingOverrideAnnotationFix();

    public String getDisplayName(){
        return "Missing @Override annotation";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Missing @Override annotation on '#ref' #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class MissingOverrideAnnotationFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Add @Override annotation";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)){
                return;
            }
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiModifierListOwner parent =
                    (PsiModifierListOwner) identifier.getParent();
            try{
                final PsiManager psiManager = parent.getManager();
                final PsiElementFactory factory = psiManager
                        .getElementFactory();
                final PsiAnnotation annotation = factory
                        .createAnnotationFromText("@java.lang.Override",
                                                  parent);
                final PsiModifierList modifierList =
                        parent.getModifierList();
                modifierList.addAfter(annotation, null);
            } catch(IncorrectOperationException e){
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new MissingOverrideAnnotationVisitor(this, inspectionManager,
                                                    onTheFly);
    }

    private static class MissingOverrideAnnotationVisitor
            extends BaseInspectionVisitor{
        private MissingOverrideAnnotationVisitor(BaseInspection inspection,
                                                 InspectionManager inspectionManager,
                                                 boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            if(method.isConstructor()){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE) ||
                    method.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            final PsiManager manager = method.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)){
                return;
            }
            if(!isOverridden(method)){
                return;
            }
            if(hasOverrideAnnotation(method)){
                return;
            }
            registerMethodError(method);
        }
    }

    private static boolean hasOverrideAnnotation(PsiModifierListOwner element){
        final PsiModifierList modifierList = element.getModifierList();
        if(modifierList == null){
            return false;
        }
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        if(annotations == null){
            return false;
        }
        for(final PsiAnnotation annotation : annotations){
            final PsiJavaCodeReferenceElement reference =
                    annotation.getNameReferenceElement();
            final PsiClass annotationClass =
                    (PsiClass) reference.resolve();
            if(annotationClass == null){
                return false;
            }
            final String annotationClassName =
                    annotationClass.getQualifiedName();
            if("java.lang.Override".equals(annotationClassName)){
                return true;
            }
        }
        return false;
    }

    private  static boolean isOverridden(PsiMethod method){
        final PsiMethod[] superMethods = method.findSuperMethods();
        for(PsiMethod superMethod : superMethods){
            final PsiClass containingClass = superMethod.getContainingClass();
            if(containingClass == null || !containingClass.isInterface()){
                return true;
            }
        }
        return false;
    }
}