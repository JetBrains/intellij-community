package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ig.*;

public class RedundantImplementsInspection extends ClassInspection{
    private final RedundantImplementsFix fix = new RedundantImplementsFix();

    public String getID(){
        return "RedundantInterfaceDeclaration";
    }

    public String getDisplayName(){
        return "Redundant interface declaration";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Redundant interface declaration '#ref' #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class RedundantImplementsFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove redundant interface declaration";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)){
                return;
            }
            final PsiElement implementReference = descriptor.getPsiElement();
            deleteElement(implementReference);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new RedundantImplementsVisitor(this, inspectionManager,
                                              onTheFly);
    }

    private static class RedundantImplementsVisitor
            extends BaseInspectionVisitor{
        private RedundantImplementsVisitor(BaseInspection inspection,
                                           InspectionManager inspectionManager,
                                           boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            if(aClass.isAnnotationType()){
                return;
            }
            if(aClass.isInterface()){
                checkInterface(aClass);
            } else{
                checkConcreteClass(aClass);
            }
        }

        private void checkInterface(PsiClass aClass){
            final PsiReferenceList extendsList = aClass.getExtendsList();
            final PsiJavaCodeReferenceElement[] extendsElements =
                    extendsList.getReferenceElements();
            for(int i = 0; i < extendsElements.length; i++){
                final PsiJavaCodeReferenceElement implementsElement =
                        extendsElements[i];
                final PsiElement referent = implementsElement.resolve();
                if(referent != null && referent instanceof PsiClass){
                    final PsiClass implementedClass = (PsiClass) referent;
                    checkExtendedInterface(implementedClass, implementsElement,
                                           extendsElements);
                }
            }
        }

        private void checkConcreteClass(PsiClass aClass){
            final PsiReferenceList extendsList = aClass.getExtendsList();
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if(extendsList == null || implementsList == null){
                return;
            }
            final PsiJavaCodeReferenceElement[] extendsElements =
                    extendsList.getReferenceElements();
            final PsiJavaCodeReferenceElement[] implementsElements =
                    implementsList.getReferenceElements();
            for(int i = 0; i < implementsElements.length; i++){
                final PsiJavaCodeReferenceElement implementsElement =
                        implementsElements[i];
                final PsiElement referent = implementsElement.resolve();
                if(referent != null && referent instanceof PsiClass){
                    final PsiClass implementedClass = (PsiClass) referent;
                    checkImplementedClass(implementedClass, implementsElement,
                                          extendsElements, implementsElements);
                }
            }
        }

        private void checkImplementedClass(PsiClass implementedClass,
                                           PsiJavaCodeReferenceElement implementsElement,
                                           PsiJavaCodeReferenceElement[] extendsElements,
                                           PsiJavaCodeReferenceElement[] implementsElements){
            for(int j = 0; j < extendsElements.length; j++){
                final PsiJavaCodeReferenceElement extendsElement =
                        extendsElements[j];
                final PsiElement extendsReferent = extendsElement.resolve();
                if(extendsReferent != null &&
                                   extendsReferent instanceof PsiClass){
                    final PsiClass extendedClass = (PsiClass) extendsReferent;
                    if(extendedClass.isInheritor(implementedClass, true)){
                        registerError(implementsElement);
                        return;
                    }
                }
            }
            for(int j = 0; j < implementsElements.length; j++){
                final PsiJavaCodeReferenceElement testImplementElement =
                        implementsElements[j];
                if(!testImplementElement.equals(implementsElement)){
                    final PsiElement implementsReferent =
                            testImplementElement.resolve();
                    if(implementsReferent != null &&
                                       implementsReferent instanceof PsiClass){
                        final PsiClass testImplementedClass =
                                (PsiClass) implementsReferent;
                        if(testImplementedClass.isInheritor(implementedClass,
                                                            true)){
                            registerError(implementsElement);
                            return;
                        }
                    }
                }
            }
        }

        private void checkExtendedInterface(PsiClass implementedClass,
                                            PsiJavaCodeReferenceElement implementsElement,
                                            PsiJavaCodeReferenceElement[] extendsElements){

            for(int j = 0; j < extendsElements.length; j++){
                final PsiJavaCodeReferenceElement testImplementElement =
                        extendsElements[j];
                if(!testImplementElement.equals(implementsElement)){
                    final PsiElement implementsReferent =
                            testImplementElement.resolve();
                    if(implementsReferent != null &&
                                       implementsReferent instanceof PsiClass){
                        final PsiClass testImplementedClass =
                                (PsiClass) implementsReferent;
                        if(testImplementedClass.isInheritor(implementedClass,
                                                            true)){
                            registerError(implementsElement);
                            return;
                        }
                    }
                }
            }
        }
    }
}
