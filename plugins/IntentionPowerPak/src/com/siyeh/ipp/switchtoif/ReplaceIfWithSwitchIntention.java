package com.siyeh.ipp.switchtoif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.DeclarationUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;

import java.util.*;

public class ReplaceIfWithSwitchIntention extends Intention{
    public String getText(){
        return "Replace if with switch";
    }

    public String getFamilyName(){
        return "Replace If With Switch";
    }

    public PsiElementPredicate getElementPredicate(){
        return new IfToSwitchPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiJavaToken switchToken =
                (PsiJavaToken) findMatchingElement(file, editor);
        PsiIfStatement ifStatement = (PsiIfStatement) switchToken.getParent();
        boolean breaksNeedRelabeled = false;
        PsiStatement breakTarget = null;
        String labelString = "";
        if(ControlFlowUtils.statementContainsExitingBreak(ifStatement)){
            // what a pain.
            PsiElement ancestor = ifStatement.getParent();
            while(ancestor != null){
                if(ancestor instanceof PsiForStatement ||
                                        ancestor instanceof PsiDoWhileStatement ||
                                        ancestor instanceof PsiWhileStatement ||
                                        ancestor instanceof PsiSwitchStatement){
                    breakTarget = (PsiStatement) ancestor;
                    break;
                }
                ancestor = ancestor.getParent();
            }
            if(breakTarget != null){
                labelString = CaseUtil.findUniqueLabel(ifStatement, "Label");
                breaksNeedRelabeled = true;
            }
        }
        final PsiIfStatement statementToReplace = ifStatement;
        final StringBuffer switchStatementBuffer = new StringBuffer(1024);
        final PsiExpression caseExpression =
                CaseUtil.getCaseExpression(ifStatement);
        switchStatementBuffer.append("switch(" + caseExpression.getText() +
                ')');
        switchStatementBuffer.append('{');
        final List branches = new ArrayList(20);
        while(true){
            final Set topLevelVariables = new HashSet(5);
            final Set innerVariables = new HashSet(5);
            final PsiExpression condition = ifStatement.getCondition();
            final PsiExpression[] labels =
                    getValuesFromCondition(condition, caseExpression);
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            DeclarationUtils.calculateVariablesDeclared(thenBranch,
                                                        topLevelVariables,
                                                        innerVariables,
                                                        true);
            final IfStatementBranch ifBranch = new IfStatementBranch();
            ifBranch.setInnerVariables(innerVariables);
            ifBranch.setTopLevelVariables(topLevelVariables);
            ifBranch.setStatement(thenBranch);
            for(int i = 0; i < labels.length; i++){
                final PsiExpression label = labels[i];
                final String labelText = label.getText();
                ifBranch.addCondition(labelText);
            }
            branches.add(ifBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();

            if(elseBranch instanceof PsiIfStatement){
                ifStatement = (PsiIfStatement) elseBranch;
            } else if(elseBranch == null){
                break;
            } else{
                final Set elseTopLevelVariables = new HashSet(5);
                final Set elseInnerVariables = new HashSet(5);
                DeclarationUtils.calculateVariablesDeclared(elseBranch,
                                                            elseTopLevelVariables,
                                                            elseInnerVariables,
                                                            true);
                final IfStatementBranch elseIfBranch = new IfStatementBranch();
                elseIfBranch.setInnerVariables(elseInnerVariables);
                elseIfBranch.setTopLevelVariables(elseTopLevelVariables);
                elseIfBranch.setElse();
                elseIfBranch.setStatement(elseBranch);
                branches.add(elseIfBranch);
                break;
            }
        }

        for(Iterator iterator = branches.iterator(); iterator.hasNext();){
            final IfStatementBranch branch =
                    (IfStatementBranch) iterator.next();
            boolean hasConflicts = false;
            for(Iterator innerIterator = branches.iterator();
                innerIterator.hasNext();){
                final IfStatementBranch testBranch =
                        (IfStatementBranch) innerIterator.next();
                if(branch.topLevelDeclarationsConfictWith(testBranch)){
                    hasConflicts = true;
                }
            }

            final PsiStatement branchStatement = branch.getStatement();
            if(branch.isElse()){
                dumpDefaultBranch(switchStatementBuffer, branchStatement,
                                  hasConflicts,
                                  breaksNeedRelabeled, labelString);
            } else{
                final List conditions = branch.getConditions();
                dumpBranch(switchStatementBuffer, conditions, branchStatement,
                           hasConflicts, breaksNeedRelabeled, labelString);
            }
        }
        switchStatementBuffer.append('}');
        final String switchStatementString = switchStatementBuffer.toString();
        if(breaksNeedRelabeled){
            final int length = switchStatementBuffer.length();
            final StringBuffer out = new StringBuffer(length);
            out.append(labelString + ':');
            termReplace(out, breakTarget, statementToReplace,
                        switchStatementString);
            final String newStatement = out.toString();
            replaceStatement(project, newStatement, breakTarget);
        } else{
            replaceStatement(project, switchStatementString,
                             statementToReplace);
        }
    }

    private void termReplace(StringBuffer out, PsiElement target,
                             PsiElement replace, String stringToReplaceWith){
        if(target.equals(replace)){
            out.append(stringToReplaceWith);
        } else if(target.getChildren() != null &&
                          target.getChildren().length != 0){
            final PsiElement[] children = target.getChildren();
            for(int i = 0; i < children.length; i++){
                final PsiElement child = children[i];
                termReplace(out, child, replace, stringToReplaceWith);
            }
        } else{
            final String text = target.getText();
            out.append(text);
        }
    }

    private PsiExpression[] getValuesFromCondition(PsiExpression condition,
                                                   PsiExpression caseExpression){
        final List values = new ArrayList(10);
        final PsiBinaryExpression binaryCond = (PsiBinaryExpression) condition;
        getValuesFromExpression(binaryCond, caseExpression, values);
        return (PsiExpression[]) values.toArray(new PsiExpression[values.size()]);
    }

    private void getValuesFromExpression(PsiBinaryExpression binaryCond,
                                         PsiExpression caseExpression,
                                         List values){
        final PsiExpression lhs = binaryCond.getLOperand();
        final PsiExpression rhs = binaryCond.getROperand();
        final PsiJavaToken sign = binaryCond.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(JavaTokenType.OROR.equals(tokenType)){
            getValuesFromExpression((PsiBinaryExpression) lhs, caseExpression,
                                    values);
            getValuesFromExpression((PsiBinaryExpression) rhs, caseExpression,
                                    values);
        } else{
            if(EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                                                           rhs)){
                values.add(lhs);
            } else{
                values.add(rhs);
            }
        }
    }

    private static void dumpBranch(StringBuffer switchStatementString,
                                   List labels, PsiStatement body, boolean wrap,
                                   boolean renameBreaks, String breakLabelName){
        dumpLabels(switchStatementString, labels);
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                 breakLabelName);
    }

    private static void dumpDefaultBranch(StringBuffer switchStatementString,
                                          PsiStatement body, boolean wrap,
                                          boolean renameBreaks,
                                          String breakLabelName){
        switchStatementString.append("default: ");
        dumpBody(switchStatementString, body, wrap, renameBreaks,
                 breakLabelName);
    }

    private static void dumpLabels(StringBuffer switchStatementString,
                                   List labels){
        for(Iterator iterator = labels.iterator(); iterator.hasNext();){
            final String exp = (String) iterator.next();
            switchStatementString.append("case ");
            switchStatementString.append(exp);
            switchStatementString.append(": ");
        }
    }

    private static void dumpBody(StringBuffer switchStatementString,
                                 PsiStatement bodyStatement, boolean wrap,
                                 boolean renameBreaks, String breakLabelName){
        if(bodyStatement instanceof PsiBlockStatement){
            if(wrap){
                appendElement(switchStatementString, bodyStatement,
                              renameBreaks, breakLabelName);
            } else{
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement) bodyStatement).getCodeBlock();
                final PsiElement[] children = codeBlock.getChildren();
                //skip the first and last members, to unwrap the block
                for(int i = 1; i < children.length - 1; i++){
                    final PsiElement child = children[i];
                    appendElement(switchStatementString, child, renameBreaks,
                                  breakLabelName);
                }
            }
        } else{
            if(wrap){
                switchStatementString.append('{');
                appendElement(switchStatementString, bodyStatement,
                              renameBreaks, breakLabelName);
                switchStatementString.append('}');
            } else{
                appendElement(switchStatementString, bodyStatement,
                              renameBreaks, breakLabelName);
            }
        }
        if(ControlFlowUtils.statementMayCompleteNormally(bodyStatement)){
            switchStatementString.append("break; ");
        }
    }

    private static void appendElement(StringBuffer switchStatementString,
                                      PsiElement element,
                                      boolean renameBreakElements,
                                      String breakLabelString){
        final String text = element.getText();
        if(!renameBreakElements){
            switchStatementString.append(text);
        } else if(element instanceof PsiBreakStatement){
            final PsiIdentifier identifier =
                    ((PsiBreakStatement) element).getLabelIdentifier();
            if(identifier == null){
                switchStatementString.append("break " + breakLabelString + ';');
            } else{
                final String identifierText = identifier.getText();
                if("".equals(identifierText)){
                    switchStatementString.append("break " + breakLabelString +
                            ';');
                } else{
                    switchStatementString.append(text);
                }
            }
        } else if(element instanceof PsiBlockStatement ||
                                element instanceof PsiCodeBlock ||
                                element instanceof PsiIfStatement){
            final PsiElement[] children = element.getChildren();
            for(int i = 0; i < children.length; i++){
                final PsiElement child = children[i];
                appendElement(switchStatementString, child, renameBreakElements,
                              breakLabelString);
            }
        } else{
            switchStatementString.append(text);
        }
    }
}
