package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class CheckForOutOfMemoryOnLargeArrayAllocationInspection
        extends ExpressionInspection{
    /**
     * @noinspection PublicField
     */
    public int m_limit = 64;

    public String getDisplayName(){
        return "Large array allocation with no OutOfMemoryError check";
    }

    public String getGroupDisplayName(){
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Large array allocation which is not checked for out-of-memory condition #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleIntegerFieldOptionsPanel("Maximum number of elements ",
                                                  this, "m_limit");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new CheckForOutOfMemoryOnLargeArrayAllocationVisitor(this,
                                                                    inspectionManager,
                                                                    onTheFly);
    }

    private class CheckForOutOfMemoryOnLargeArrayAllocationVisitor
            extends BaseInspectionVisitor{
        private CheckForOutOfMemoryOnLargeArrayAllocationVisitor(BaseInspection inspection,
                                                                 InspectionManager inspectionManager,
                                                                 boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if(!(type instanceof PsiArrayType)){
                return;
            }
            int size = 1;
            final PsiExpression[] dimensions = expression.getArrayDimensions();
            for(int i = 0; i < dimensions.length; i++){
                final PsiExpression dimension = dimensions[i];
                final Integer intValue =
                        (Integer) ConstantExpressionUtil.computeCastTo(dimension,
                                                                       PsiType.INT);
                if(intValue != null){
                    size *= intValue.intValue();
                }
            }
            if(size <= m_limit){
                return;
            }
            if(OOMECaught(expression)){
                return;
            }
            registerError(expression);
        }
    }

    private boolean OOMECaught(PsiElement element){
        PsiElement currentElement = element;
        while(true){
            final PsiTryStatement containingTryStatement =
                    PsiTreeUtil.getParentOfType(currentElement,
                                                PsiTryStatement.class);
            if(containingTryStatement == null){
                return false;
            }
            if(catchesOOME(containingTryStatement)){
                return true;
            }
            currentElement = containingTryStatement;
        }
    }

    private static boolean catchesOOME(PsiTryStatement statement){
        final PsiCatchSection[] sections = statement.getCatchSections();
        for(int i = 0; i < sections.length; i++){
            final PsiCatchSection section = sections[i];
            final PsiType catchType = section.getCatchType();
            if(catchType!=null){
                final String typeText = catchType.getCanonicalText();
                if("java.lang.OutOfMemoryError".equals(typeText)){
                    return true;
                }
            }
        }
        return false;
    }

}
