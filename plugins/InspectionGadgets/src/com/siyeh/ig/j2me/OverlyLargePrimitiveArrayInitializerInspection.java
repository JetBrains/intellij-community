package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class OverlyLargePrimitiveArrayInitializerInspection
        extends ExpressionInspection{
    /**
     * @noinspection PublicField
     */
    public int m_limit = 64;

    public String getDisplayName(){
        return "Overly large initializer for array of primitive type";
    }

    public String getGroupDisplayName(){
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiExpression expression = (PsiExpression) location;
        final int numElements = calculateNumElements(expression);
        return "Primitive array initializer with too many elements (" +
                numElements + ") #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleIntegerFieldOptionsPanel("Maximum number of elements ",
                                                  this, "m_limit");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new OverlyLargePrimitiveArrayInitializerVisitor(this,
                                                               inspectionManager,
                                                               onTheFly);
    }

    private class OverlyLargePrimitiveArrayInitializerVisitor
            extends BaseInspectionVisitor{
        private OverlyLargePrimitiveArrayInitializerVisitor(BaseInspection inspection,
                                                            InspectionManager inspectionManager,
                                                            boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression){
            super.visitArrayInitializerExpression(expression);
            final PsiType type = expression.getType();
            if(type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if(!(componentType instanceof PsiPrimitiveType)) {
                return;
            }
            final int numElements = calculateNumElements(expression);
            if(numElements <= m_limit) {
                return;
            }
            registerError(expression);
        }
    }

    private int calculateNumElements(PsiExpression expression){
        if(expression instanceof PsiArrayInitializerExpression) {
            final PsiArrayInitializerExpression arrayExpression = (PsiArrayInitializerExpression) expression;
            final PsiExpression[] initializers = arrayExpression.getInitializers();
            int out = 0;
            for(final PsiExpression initializer : initializers){
                out += calculateNumElements(initializer);
            }
            return out;
        }
        return 1;
    }
}
