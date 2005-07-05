package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) descriptor.getPsiElement();
            final String referencedClassName = referenceElement.getText();
            final PsiClass iface = (PsiClass) referenceElement.resolve();
            assert iface != null;
            final PsiField[] allFields = iface.getAllFields();

            final PsiClass implementingClass =
                    PsiTreeUtil.getParentOfType(referenceElement,
                                                           PsiClass.class);
            final PsiManager manager = referenceElement.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            assert implementingClass != null;
            final SearchScope searchScope = implementingClass.getUseScope();
            for(final PsiField field : allFields){
                final PsiReference[] references =
                        searchHelper.findReferences(field, searchScope, false);

                for(PsiReference reference1 : references){
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression) reference1;
                    if(reference.isQualified()){
                        final PsiExpression qualifier =
                                reference.getQualifierExpression();
                        final String referenceName =
                                reference.getReferenceName();
                        if(qualifier instanceof PsiReferenceExpression){
                            final PsiElement referent =
                                    ((PsiReferenceExpression) qualifier).resolve();
                            if(referent!=null && !referent.equals(iface)){
                                replaceExpression(reference,
                                                  referencedClassName + '.' +
                                                          referenceName);
                            }
                        } else{
                            replaceExpression(reference,
                                              referencedClassName + '.' +
                                                      referenceName);
                        }
                    } else{
                        final String referenceText = reference.getText();
                        replaceExpression(reference,
                                          referencedClassName + '.' +
                                                  referenceText);
                    }
                }
            }
            deleteElement(referenceElement);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new StaticInheritanceVisitor();
    }

    private static class StaticInheritanceVisitor extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if(implementsList == null){
                return;
            }
            final PsiJavaCodeReferenceElement[] refs =
                    implementsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement ref : refs){
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
            for(final PsiClass parentInterface : parentInterfaces){
                if(!interfaceContainsOnlyConstants(parentInterface)){
                    return false;
                }
            }
            return true;
        }
    }
}
