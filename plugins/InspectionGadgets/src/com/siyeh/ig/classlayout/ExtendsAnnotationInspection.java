package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class ExtendsAnnotationInspection extends ClassInspection{

    public String getID(){
        return "ClassExplicitlyAnnotation";
    }

    public String getDisplayName(){
        return "Class extends annotation interface";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiClass containingClass = ClassUtils.getContainingClass(location);
        return "Class "+ containingClass.getName()+" explicitly extends annotation interface '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ExtendsAnnotationVisitor(this, inspectionManager, onTheFly);
    }

    private static class ExtendsAnnotationVisitor extends BaseInspectionVisitor{
        private ExtendsAnnotationVisitor(BaseInspection inspection,
                                     InspectionManager inspectionManager,
                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            final PsiManager manager = aClass.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
                       languageLevel.equals(LanguageLevel.JDK_1_4)){
                return;
            }
            if(aClass.isAnnotationType()){
                return;
            }
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if(extendsList != null){
                final PsiJavaCodeReferenceElement[] elements =
                        extendsList.getReferenceElements();
                for(int i = 0; i < elements.length; i++){
                    final PsiJavaCodeReferenceElement element = elements[i];
                    final PsiElement referent = element.resolve();
                    if(referent instanceof PsiClass){
                                ((PsiClass) referent).isAnnotationType();
                        if(((PsiClass) referent).isAnnotationType()){
                            registerError(element);
                        }
                    }
                }
            }
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if(implementsList != null){
                final PsiJavaCodeReferenceElement[] elements =
                        implementsList.getReferenceElements();
                for(int i = 0; i < elements.length; i++){
                    final PsiJavaCodeReferenceElement element = elements[i];
                    final PsiElement referent = element.resolve();
                    if(referent instanceof PsiClass){
                                ((PsiClass) referent).isAnnotationType();
                        if(((PsiClass) referent).isAnnotationType()){
                            registerError(element);
                        }
                    }
                }
            }
        }
    }
}
