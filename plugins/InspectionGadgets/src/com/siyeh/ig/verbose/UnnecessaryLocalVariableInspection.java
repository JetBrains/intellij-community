package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.fixes.InlineVariableFix;
import com.siyeh.ig.psiutils.VariableAssignedVisitor;
import com.siyeh.ig.psiutils.VariableUsedVisitor;

import javax.swing.*;

public class UnnecessaryLocalVariableInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreImmediatelyReturnedVariables = false;
    private final InlineVariableFix fix = new InlineVariableFix();

    public String getDisplayName() {
        return "Redundant local variable";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore immediately returned variables",
                                              this, "m_ignoreImmediatelyReturnedVariables");
    }
    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Local variable #ref is redundant #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryLocalVariableVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    private  class UnnecessaryLocalVariableVisitor extends BaseInspectionVisitor {
        private UnnecessaryLocalVariableVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            if (isCopyVariable(variable)) {
                registerVariableError(variable);
            } else if (!m_ignoreImmediatelyReturnedVariables &&
                               isImmediatelyReturned(variable)) {
                registerVariableError(variable);
            } else if (isImmediatelyThrown(variable)) {
                registerVariableError(variable);
            } else if (isImmediatelyAssigned(variable)) {
                registerVariableError(variable);
            }else if (isImmediatelyAssignedAsDeclaration(variable)) {
                registerVariableError(variable);
            }
        }
    }

    private static boolean isCopyVariable(PsiVariable variable) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
            return false;
        }
        if (!(initializer instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement referent = ((PsiReference) initializer).resolve();
        if (referent == null) {
            return false;
        }
        if (!(referent instanceof PsiLocalVariable || referent instanceof PsiParameter)) {
            return false;
        }
        final PsiCodeBlock containingScope =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (containingScope == null) {
            return false;
        }
        final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable);
        containingScope.accept(visitor);
        if (visitor.isAssigned()) {
            return false;
        }

        final PsiVariable initialization = (PsiVariable) referent;
        final VariableAssignedVisitor visitor2 = new VariableAssignedVisitor(initialization);
        containingScope.accept(visitor2);
        if (visitor2.isAssigned()) {
            return false;
        }
        if (!initialization.hasModifierProperty(PsiModifier.FINAL)
                && variable.hasModifierProperty(PsiModifier.FINAL)) {
            if (variableIsUsedInInnerClass(containingScope, variable)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isImmediatelyReturned(PsiVariable variable) {

        final PsiCodeBlock containingScope =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (containingScope == null) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement =
                PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
        if (declarationStatement == null) {
            return false;
        }
        PsiStatement nextStatement = null;
        final PsiStatement[] statements = containingScope.getStatements();
        for (int i = 0; i < statements.length - 1; i++) {
            if (statements[i].equals(declarationStatement)) {
                nextStatement = statements[i + 1];
            }
        }
        if (nextStatement == null) {
            return false;
        }
        if (!(nextStatement instanceof PsiReturnStatement)) {
            return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement) nextStatement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) {
            return false;
        }
        if (!(returnValue instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement referent = ((PsiReference) returnValue).resolve();
        return !(referent == null || !referent.equals(variable));
    }
    private static boolean isImmediatelyThrown(PsiVariable variable) {

        final PsiCodeBlock containingScope =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (containingScope == null) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement =
                PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
        if (declarationStatement == null) {
            return false;
        }
        PsiStatement nextStatement = null;
        final PsiStatement[] statements = containingScope.getStatements();
        for (int i = 0; i < statements.length - 1; i++) {
            if (statements[i].equals(declarationStatement)) {
                nextStatement = statements[i + 1];
            }
        }
        if (nextStatement == null) {
            return false;
        }
        if (!(nextStatement instanceof PsiThrowStatement)) {
            return false;
        }
        final PsiThrowStatement throwStatement = (PsiThrowStatement) nextStatement;
        final PsiExpression returnValue = throwStatement.getException();
        if (returnValue == null) {
            return false;
        }
        if (!(returnValue instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement referent = ((PsiReference) returnValue).resolve();
        return !(referent == null || !referent.equals(variable));
    }

    private static boolean isImmediatelyAssigned(PsiVariable variable) {

        final PsiCodeBlock containingScope =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (containingScope == null) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement =
                PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
        if (declarationStatement == null) {
            return false;
        }

        PsiStatement nextStatement = null;
        int followingStatementNumber = 0;
        final PsiStatement[] statements = containingScope.getStatements();
        for (int i = 0; i < statements.length - 1; i++) {
            if (statements[i].equals(declarationStatement)) {
                nextStatement = statements[i + 1];
                followingStatementNumber = i + 2;
            }
        }
        if (nextStatement == null) {
            return false;
        }
        if (!(nextStatement instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) nextStatement;
        final PsiExpression expression = expressionStatement.getExpression();
        if (expression == null) {
            return false;
        }
        if (!(expression instanceof PsiAssignmentExpression)) {
            return false;
        }
        final PsiExpression rhs = ((PsiAssignmentExpression) expression).getRExpression();
        if (rhs == null) {
            return false;
        }
        if (!(rhs instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement referent = ((PsiReference) rhs).resolve();
        if (referent == null || !referent.equals(variable)) {
            return false;
        }
        for (int i = followingStatementNumber; i < statements.length; i++) {
            if (variableIsUsedInStatement(statements[i], variable)) {
                return false;
            }

        }
        return true;
    }


    private static boolean isImmediatelyAssignedAsDeclaration(PsiVariable variable) {

        final PsiCodeBlock containingScope =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (containingScope == null) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement =
                PsiTreeUtil.getParentOfType(variable, PsiDeclarationStatement.class);
        if (declarationStatement == null) {
            return false;
        }

        PsiStatement nextStatement = null;
        int followingStatementNumber = 0;
        final PsiStatement[] statements = containingScope.getStatements();
        for (int i = 0; i < statements.length - 1; i++) {
            if (statements[i].equals(declarationStatement)) {
                nextStatement = statements[i + 1];
                followingStatementNumber = i + 2;
            }
        }
        if (nextStatement == null) {
            return false;
        }
        if (!(nextStatement instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement) nextStatement;
        final PsiElement[] declarations = declaration.getDeclaredElements();
        if (declarations == null) {
            return false;
        }
        if (declarations.length != 1) {
            return false;
        }
        if (!(declarations[0] instanceof PsiVariable)) {
            return false;
        }

        final PsiExpression rhs = ((PsiVariable) declarations[0]).getInitializer();
        if (rhs == null) {
            return false;
        }
        if (!(rhs instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement referent = ((PsiReference) rhs).resolve();
        if (referent == null || !referent.equals(variable)) {
            return false;
        }
        for (int i = followingStatementNumber; i < statements.length; i++) {
            if (variableIsUsedInStatement(statements[i], variable)) {
                return false;
            }

        }
        return true;
    }

    private static boolean variableIsUsedInInnerClass(PsiCodeBlock block,
                                                      PsiVariable variable) {

        final VariableUsedInInnerClassVisitor visitor
                = new VariableUsedInInnerClassVisitor(variable);
        block.accept(visitor);
        return visitor.isUsedInInnerClass();
    }

    private static boolean variableIsUsedInStatement(PsiStatement statement,
                                                     PsiVariable variable) {

        final VariableUsedVisitor visitor
                = new VariableUsedVisitor(variable);
        statement.accept(visitor);
        return visitor.isUsed();
    }

}
