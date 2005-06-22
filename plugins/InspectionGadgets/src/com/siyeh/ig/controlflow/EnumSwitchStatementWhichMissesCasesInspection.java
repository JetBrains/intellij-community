package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EnumSwitchStatementWhichMissesCasesInspection extends StatementInspection {

    public String getDisplayName() {
        return "Enum 'switch' statement that misses case";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiSwitchStatement switchStatement = (PsiSwitchStatement) location.getParent();
        assert switchStatement != null;
        final PsiExpression switchStatementExpression = switchStatement.getExpression();
        final PsiType switchStatementType = switchStatementExpression.getType();
        final String switchStatementTypeText = switchStatementType.getPresentableText();
        return "'#ref' statement on enumerated type '" + switchStatementTypeText+
                       "' misses cases #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryDefaultVisitor();
    }

    private static class UnnecessaryDefaultVisitor
            extends StatementInspectionVisitor {


        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            if (!switchStatementMissingCases(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean switchStatementMissingCases(PsiSwitchStatement statement) {
            final PsiExpression expression = statement.getExpression();
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClass aClass = ((PsiClassType) type).resolve();
            if (aClass == null) {
                return false;
            }
            if (!aClass.isEnum()) {
                return false;
            }
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return false;
            }
            int numCases = 0;
            for(final PsiStatement child : statements){
                if(child instanceof PsiSwitchLabelStatement){
                    if(!((PsiSwitchLabelStatement) child).isDefaultCase()){
                        numCases++;
                    }
                }
            }
            final PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for(final PsiField field : fields){
                if(field.getType().equals(type)){
                    numEnums++;
                }
            }
            return numEnums != numCases;
        }
    }

}