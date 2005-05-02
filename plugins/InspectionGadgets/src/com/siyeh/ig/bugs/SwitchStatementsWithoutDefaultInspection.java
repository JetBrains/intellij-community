package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SwitchStatementsWithoutDefaultInspection extends StatementInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreFullyCoveredEnums = true;

    public String getID(){
        return "SwitchStatementWithoutDefaultBranch";
    }

    public String getDisplayName() {
        return "'switch' statement without 'default' branch";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' statement without 'default' branch #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore if all cases of an enumerated type are covered",
                this, "m_ignoreFullyCoveredEnums");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementsWithoutDefaultVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementsWithoutDefaultVisitor extends StatementInspectionVisitor {
        private SwitchStatementsWithoutDefaultVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            if (switchStatementHasDefault(statement)) {
                return;
            }
            if (m_ignoreFullyCoveredEnums && switchStatementIsFullyCoveredEnum(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean switchStatementHasDefault(PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            for(final PsiStatement child : statements){
                if(child instanceof PsiSwitchLabelStatement &&
                        ((PsiSwitchLabelStatement) child).isDefaultCase()){
                    return true;
                }
            }
            return false;
        }

        private boolean switchStatementIsFullyCoveredEnum(PsiSwitchStatement statement) {
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
            int numCases = 0;
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return false;
            }
            for(final PsiStatement child : statements){
                if(child instanceof PsiSwitchLabelStatement){
                    numCases++;
                }
            }
            final PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for(final PsiField field : fields){
                final PsiType fieldType = field.getType();
                if(fieldType.equals(type)){
                    numEnums++;
                }
            }
            return numEnums == numCases;
        }
    }

}