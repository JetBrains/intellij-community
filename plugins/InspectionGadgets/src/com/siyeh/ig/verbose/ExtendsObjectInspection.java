package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ig.*;

public class ExtendsObjectInspection extends ClassInspection{
    private final ExtendsObjectFix fix = new ExtendsObjectFix();

    public String getID(){
        return "ClassExplicitlyExtendsObject";
    }

    public String getDisplayName(){
        return "Class explicitly extends java.lang.Object";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Class '#ref' explicitly extends java.lang.Object #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove redundant 'extends Object'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)){
                return;
            }
            final PsiElement extendClassIdentifier = descriptor.getPsiElement();
            final PsiClass element =
                    (PsiClass) extendClassIdentifier.getParent();
            final PsiReferenceList extendsList = element.getExtendsList();
            final PsiJavaCodeReferenceElement[] elements =
                    extendsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement element1 : elements){
                deleteElement(element1);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ExtendsObjectVisitor(this, inspectionManager, onTheFly);
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor{
        private ExtendsObjectVisitor(BaseInspection inspection,
                                     InspectionManager inspectionManager,
                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            if(aClass.isInterface() || aClass.isAnnotationType()){
                return;
            }
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if(extendsList != null){
                final PsiJavaCodeReferenceElement[] elements =
                        extendsList.getReferenceElements();
                for(final PsiJavaCodeReferenceElement element : elements){
                    final PsiElement referent = element.resolve();
                    if(referent instanceof PsiClass){
                        final String className =
                                ((PsiClass) referent).getQualifiedName();
                        if("java.lang.Object".equals(className)){
                            registerClassError(aClass);
                        }
                    }
                }
            }
        }
    }
}
