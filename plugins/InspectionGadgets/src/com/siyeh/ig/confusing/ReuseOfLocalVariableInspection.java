package com.siyeh.ig.confusing;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReuseOfLocalVariableInspection
        extends ExpressionInspection{
    private final AssignmentToCatchBlockParameterFix fix =
            new AssignmentToCatchBlockParameterFix();

    public String getDisplayName(){
        return "Reuse of local variable";
    }

    public String getGroupDisplayName(){
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Reuse of local variable #ref #loc ";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AssignmentToCatchBlockParameterFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Split local variable";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiLocalVariable variable = (PsiLocalVariable) ref.resolve();
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) ref
                    .getParent();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) assignment.getParent();
            final String originalVariableName = assignment.getLExpression().getText();
            final PsiManager manager = variable.getManager();
            final PsiType type = variable.getType();
            final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
            final PsiBlockStatement assignmentBlock =
                    PsiTreeUtil.getParentOfType(assignmentStatement,
                                                PsiBlockStatement.class);
            final PsiCodeBlock variableBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            final SuggestedNameInfo suggestions =
                    codeStyleManager
                            .suggestVariableName(VariableKind.LOCAL_VARIABLE,
                                                 originalVariableName,
                                                 assignment.getLExpression(), type);
            final String[] names = suggestions.names;
            final String baseName;
            if(names != null && names.length > 0){
                baseName = names[0];
            } else{
                baseName = "value";
            }
            final String newVariableName =
                    codeStyleManager.suggestUniqueVariableName(baseName,
                                                               variableBlock,
                                                               false);


            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiReference[] references =
                    searchHelper
                            .findReferences(variable, variable.getUseScope(),
                                            false);
            for(PsiReference reference : references){
                final PsiElement referenceElement = reference.getElement();
                if(referenceElement != null
                        && referenceElement.getTextOffset() >
                        assignmentStatement.getTextRange().getEndOffset()){
                    replaceExpression((PsiExpression) referenceElement,
                                      newVariableName);
                }
            }
            final String newStatement =
                    type.getPresentableText() + ' '
                            + newVariableName +
                            " =  " + assignment.getRExpression().getText()
                            + ';';
            replaceStatement(assignmentStatement, newStatement);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReuseOfLocalVariableVisitor();
    }

    private static class ReuseOfLocalVariableVisitor
            extends BaseInspectionVisitor{
        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            super.visitAssignmentExpression(assignment);
            if(!WellFormednessUtils.isWellFormed(assignment)){
                return;
            }
            if(!(assignment.getParent() instanceof PsiExpressionStatement)){
                return;
            }
            final PsiExpression lhs = assignment.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) lhs;
            final PsiElement referent = ref.resolve();
            if(!(referent instanceof PsiLocalVariable)){
                return;
            }
            final PsiVariable variable = (PsiVariable) referent;
            if(variable.getInitializer()==null)    //TODO: this is safe, but can be weakened
            {
                return;
            }
            final PsiJavaToken sign = assignment.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.EQ.equals(tokenType)){
                return;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if(VariableAccessUtils.variableIsUsed(rhs, variable)){
                return;
            }
            final PsiCodeBlock variableBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if(variableBlock == null){
                return;
            }

            if(loopExistsBetween(assignment, variableBlock)){
                return;
            }
            final PsiElement assignmentBlock =
                    assignment.getParent().getParent();
            if(assignmentBlock == null){
                return;
            }
            if(variableBlock.equals(assignmentBlock)){
                registerError(lhs);
            }
            final PsiStatement[] statements = variableBlock.getStatements();
            final PsiElement containingStatement = getChildWhichContainsElement(
                    variableBlock, assignment);
            int statementPosition = -1;
            for(int i = 0; i < statements.length; i++){
                if(statements[i].equals(containingStatement)){
                    statementPosition = i;
                    break;
                }
            }
            if(statementPosition == -1){
                return;
            }
            for(int i = statementPosition + 1; i < statements.length; i++){
                if(VariableAccessUtils.variableIsUsed(statements[i], variable)){
                    return;
                }
            }
            registerError(lhs);
        }

        private boolean loopExistsBetween(PsiAssignmentExpression assignment,
                                          PsiCodeBlock block){
            PsiElement elementToTest = assignment;
            while(elementToTest != null){
                if(elementToTest.equals(block)){
                    return false;
                }
                if(elementToTest instanceof PsiWhileStatement ||
                        elementToTest instanceof PsiForeachStatement ||
                        elementToTest instanceof PsiForStatement ||
                        elementToTest instanceof PsiDoWhileStatement
                        ){
                    return true;
                }
                elementToTest = elementToTest.getParent();
            }
            return false;
        }

        @Nullable
        public static PsiElement getChildWhichContainsElement(
                @NotNull PsiCodeBlock ancestor,
                @NotNull PsiElement descendant){
            PsiElement element = descendant;
            while(!element.equals(ancestor)){
                descendant = element;
                element = descendant.getParent();
                if(element == null){
                    return null;
                }
            }
            return descendant;
        }
    }
}
