package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ExtendsObjectInspection extends ClassInspection{
    private final ExtendsObjectFix fix = new ExtendsObjectFix();

    public String getID(){
        return "ClassExplicitlyExtendsObject";
    }

    public String getDisplayName(){
        return "Class explicitly extends java.lang.Object";
    }

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement extendClassIdentifier = descriptor.getPsiElement();
            final PsiClass element =
                    (PsiClass) extendClassIdentifier.getParent();
            final PsiReferenceList extendsList = element.getExtendsList();
            assert extendsList != null;
            final PsiJavaCodeReferenceElement[] elements =
                    extendsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement element1 : elements){
                deleteElement(element1);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ExtendsObjectVisitor();
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
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
