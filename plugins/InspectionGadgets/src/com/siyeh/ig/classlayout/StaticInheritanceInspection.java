package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;

public class StaticInheritanceInspection extends ClassInspection{
    public String getDisplayName(){
        return "Static inheritance";
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Interface #ref is implemented only for its static constants #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new StaticInheritanceFix();
    }

    private static class StaticInheritanceFix extends InspectionGadgetsFix{
        public String getName(){
            return "Replace inheritance with qualified references";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) descriptor.getPsiElement();
            final String text = referenceElement.getText();
            final PsiClass iface = (PsiClass) referenceElement.resolve();
            final PsiField[] allFields = iface.getAllFields();

            final PsiClass implementingClass =
                    (PsiClass) PsiTreeUtil.getParentOfType(referenceElement,
                                                           PsiClass.class);
            final PsiManager manager = referenceElement.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final LocalSearchScope searchScope =
                    new LocalSearchScope(implementingClass);
            for(int i = 0; i < allFields.length; i++){
                final PsiField field = allFields[i];
                final PsiReference[] references =
                        searchHelper.findReferences(field, searchScope, false);

                for(int j = 0; j < references.length; j++){
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression) references[j];
                    if(reference.isQualified()){
                        continue;
                    }
                    final String referenceText = reference.getText();
                    replaceExpression(project, reference,
                                      text + '.' + referenceText);
                }
            }
            deleteElement(referenceElement);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new StaticInheritanceVisitor(this, inspectionManager, onTheFly);
    }

    private static class StaticInheritanceVisitor extends BaseInspectionVisitor{
        private StaticInheritanceVisitor(BaseInspection inspection,
                                         InspectionManager inspectionManager,
                                         boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if(implementsList == null){
                return;
            }
            final PsiJavaCodeReferenceElement[] refs =
                    implementsList.getReferenceElements();
            for(int i = 0; i < refs.length; i++){
                final PsiJavaCodeReferenceElement ref = refs[i];
                final PsiClass iface = (PsiClass) ref.resolve();
                if(iface != null){
                    if(interfaceContainsOnlyConstants(iface)){
                        registerError(ref);
                    }
                }
            }
        }

        private boolean interfaceContainsOnlyConstants(PsiClass iface){
            if(iface.getAllFields().length == 0){
                // ignore it, it's either a true interface or just a marker
                return false;
            }
            if(iface.getMethods().length != 0){
                return false;
            }
            final PsiClass[] parentInterfaces = iface.getInterfaces();
            for(int i = 0; i < parentInterfaces.length; i++){
                final PsiClass parentInterface = parentInterfaces[i];
                if(!interfaceContainsOnlyConstants(parentInterface)){
                    return false;
                }
            }
            return true;
        }
    }
}
