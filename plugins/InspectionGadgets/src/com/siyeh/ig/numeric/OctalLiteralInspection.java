package com.siyeh.ig.numeric;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class OctalLiteralInspection extends ExpressionInspection{
    public String getID(){
        return "OctalInteger";
    }

    public String getDisplayName(){
        return "Octal integer";
    }

    public String getGroupDisplayName(){
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    protected String buildErrorString(PsiElement location){
        return "Octal integer #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new OctalLiteralVisitor();
    }

    private static class OctalLiteralVisitor extends BaseInspectionVisitor{

        public void visitLiteralExpression(@NotNull PsiLiteralExpression literal){
            super.visitLiteralExpression(literal);
            final PsiType type = literal.getType();
            if(type == null){
                return;
            }
            if(!(type.equals(PsiType.INT)
                    || type.equals(PsiType.LONG))){
                return;
            }
            final String text = literal.getText();
            if("0".equals(text) || "0L".equals(text)|| "0l".equals(text)){
                return;
            }
            if(text.charAt(0) != '0'){
                return;
            }
            if(text.startsWith("0x") || text.startsWith("0X")){
                return;
            }
            registerError(literal);
        }
    }
}
