package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class InstanceofIncompatibleInterfaceInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "'instanceof' with incompatible interface";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "'instanceof' incompatible interface #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new InstanceofIncompatibleInterfaceVisitor(this,
                                                          inspectionManager,
                                                          onTheFly);
    }

    private static class InstanceofIncompatibleInterfaceVisitor
            extends BaseInspectionVisitor{
        private InstanceofIncompatibleInterfaceVisitor(BaseInspection inspection,
                                                       InspectionManager inspectionManager,
                                                       boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitInstanceOfExpression(PsiInstanceOfExpression expression){
            super.visitInstanceOfExpression(expression);

            final PsiTypeElement castTypeElement = expression.getCheckType();
            if(castTypeElement == null){
                return;
            }
            final PsiType castType = castTypeElement.getType();
            if(castType == null){
                return;
            }
            if(!(castType instanceof PsiClassType)){
                return;
            }
            final PsiClass castClass = ((PsiClassType) castType).resolve();
            if(castClass == null){
                return;
            }
            if(!castClass.isInterface()){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            final PsiType operandType = operand.getType();
            if(operandType == null){
                return;
            }
            if(!(operandType instanceof PsiClassType)){
                return;
            }
            final PsiClass operandClass =
                    ((PsiClassType) operandType).resolve();
            if(operandClass == null){
                return;
            }
            if(!operandClass.isInterface()){
                return;
            }
            if(existsImplementingSubClass(operandClass, castClass)){
                return;
            }
            registerError(castTypeElement);
        }

    }

    private static boolean existsImplementingSubClass(PsiClass aClass,
                                                      PsiClass anInterface){
        if(aClass.isInheritor(anInterface, true)){
            return true;
        }
        if("java.lang.Object".equals(aClass.getQualifiedName())){
            return true;
        }
        final PsiManager psiManager = aClass.getManager();
        final Project project = psiManager.getProject();
        final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
        final SearchScope searchScope = GlobalSearchScope.allScope(project);
        final PsiClass[] inheritors =
                searchHelper.findInheritors(aClass, searchScope, true);
        for(int i = 0; i < inheritors.length; i++){
            final PsiClass inheritor = inheritors[i];
            if(inheritor.isInheritor(anInterface, true)){
                return true;
            }
        }
        return false;
    }
}
