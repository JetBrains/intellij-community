package com.siyeh.ipp.switchtoif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.SideEffectChecker;

import java.util.*;

public class ReplaceSwitchWithIfIntention extends Intention{
    public String getText(){
        return "Replace switch with if";
    }

    public String getFamilyName(){
        return "Replace Switch With If";
    }

    public PsiElementPredicate getElementPredicate(){
        return new SwitchPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{

        final PsiJavaToken switchToken =
                (PsiJavaToken) findMatchingElement(file, editor);
        final PsiSwitchStatement switchStatement =
                (PsiSwitchStatement) switchToken.getParent();
        final List allBranches = new ArrayList(10);
        final List openBranches = new ArrayList(10);
        SwitchStatementBranch currentBranch = null;

        final StringBuffer ifStatementBuffer = new StringBuffer(1024);
        final String expressionText;
        final boolean hadSideEffects;
        final String declarationString;
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiExpression switchExpression = switchStatement.getExpression();
        final CodeStyleManager codeStyleMgr = mgr.getCodeStyleManager();
        if(SideEffectChecker.mayHaveSideEffects(switchExpression)){
            hadSideEffects = true;
            final PsiType switchExpressionType = switchExpression.getType();
            final String variableName =
                    codeStyleMgr.suggestUniqueVariableName("i",
                                                           switchExpression,
                                                           true);
            expressionText = variableName;
            declarationString =
            switchExpressionType.getPresentableText() + ' ' + variableName +
                    " = " +
                    switchExpression.getText() +
                    ';';
        } else{
            hadSideEffects = false;
            declarationString = null;
            expressionText = switchExpression.getText();
        }
        final PsiCodeBlock body = switchStatement.getBody();
        final PsiStatement[] statements = body.getStatements();

        boolean renameBreaks = false;
        for(int i = 1; i < statements.length - 1; i++){
            if(CaseUtil.containsHiddenBreak(statements[i])){
                renameBreaks = true;
                break;
            }
        }

        final Set declaredVars = new HashSet(5);
        String breakLabel = null;
        if(renameBreaks){
            breakLabel = CaseUtil.findUniqueLabel(switchStatement, "Label");
            ifStatementBuffer.append(breakLabel + ':');
        }
        final PsiElement[] children = body.getChildren();

        for(int i = 1; i < children.length - 1; i++){
            final PsiElement statement = children[i];
            if(statement instanceof PsiSwitchLabelStatement){
                final PsiSwitchLabelStatement label =
                        (PsiSwitchLabelStatement) statement;
                if(currentBranch == null){
                    openBranches.clear();
                    currentBranch = new SwitchStatementBranch();
                    currentBranch.addPendingVariableDeclarations(declaredVars);
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                } else if(currentBranch.hasStatements()){
                    currentBranch = new SwitchStatementBranch();
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                }
                if(label.isDefaultCase()){
                    currentBranch.setDefault();
                } else{
                    PsiExpression value = label.getCaseValue();
                    while(value instanceof PsiParenthesizedExpression){
                        value = ((PsiParenthesizedExpression) value).getExpression();
                    }
                    final String valueText = value.getText();
                    currentBranch.addLabel(valueText);
                }
            } else{
                if(statement instanceof PsiStatement){
                    if(statement instanceof PsiDeclarationStatement){
                        final PsiDeclarationStatement decl =
                                (PsiDeclarationStatement) statement;
                        final PsiElement[] elements =
                                decl.getDeclaredElements();
                        for(int j = 0; j < elements.length; j++){
                            final PsiLocalVariable var =
                                    (PsiLocalVariable) elements[j];
                            declaredVars.add(var);
                        }
                    }
                    for(Iterator iterator = openBranches.iterator();
                        iterator.hasNext();){
                        final SwitchStatementBranch branch =
                                (SwitchStatementBranch) iterator.next();
                        branch.addStatement(statement);
                    }
                    if(!ControlFlowUtils.statementMayCompleteNormally((PsiStatement) statement)){
                        currentBranch = null;
                    }
                } else{
                    for(Iterator iterator = openBranches.iterator();
                        iterator.hasNext();){
                        final SwitchStatementBranch branch =
                                (SwitchStatementBranch) iterator.next();
                        if(statement instanceof PsiWhiteSpace){
                            branch.addWhiteSpace(statement);
                        } else{
                            branch.addComment(statement);
                        }
                    }
                }
            }
        }
        boolean firstBranch = true;
        SwitchStatementBranch defaultBranch = null;

        for(Iterator iterator = allBranches.iterator(); iterator.hasNext();){
            final SwitchStatementBranch branch =
                    (SwitchStatementBranch) iterator.next();
            if(branch.isDefault()){
                defaultBranch = branch;
            } else{
                final List labels = branch.getLabels();
                final List bodyElements = branch.getBodyElements();
                final Set pendingVariableDeclarations =
                        branch.getPendingVariableDeclarations();
                dumpBranch(ifStatementBuffer, expressionText,
                           labels, bodyElements, firstBranch,
                           renameBreaks, breakLabel,
                           pendingVariableDeclarations);
                firstBranch = false;
            }
        }
        if(defaultBranch != null){
            final List bodyElements = defaultBranch.getBodyElements();
            final Set pendingVariableDeclarations =
                    defaultBranch.getPendingVariableDeclarations();
            dumpDefaultBranch(ifStatementBuffer, bodyElements,
                              firstBranch, renameBreaks, breakLabel,
                              pendingVariableDeclarations);
        }
        final PsiElementFactory factory = mgr.getElementFactory();
        if(hadSideEffects){
            final PsiStatement declarationStatement =
                    factory.createStatementFromText(declarationString, null);
            final String ifStatementString = ifStatementBuffer.toString();
            final PsiStatement ifStatement =
                    factory.createStatementFromText(ifStatementString, null);
            PsiElement ifElement = switchStatement.replace(ifStatement);
            ifElement = codeStyleMgr.reformat(ifElement);
            final PsiElement parent = ifElement.getParent();
            final PsiElement declarationElement =
                    parent.addBefore(declarationStatement, ifElement);
            codeStyleMgr.reformat(declarationElement);
            codeStyleMgr.reformat(parent);
        } else{
            final String ifStatementString = ifStatementBuffer.toString();
            final PsiStatement newStatement =
                    factory.createStatementFromText(ifStatementString, null);
            final PsiElement replacedStatement =
                    switchStatement.replace(newStatement);
            codeStyleMgr.reformat(replacedStatement);
        }
    }

    private static void dumpBranch(StringBuffer ifStatementString,
                                   String expressionText, List labels,
                                   List bodyStatements, boolean firstBranch,
                                   boolean renameBreaks,
                                   String breakLabel,
                                   Set variableDecls){
        if(!firstBranch){
            ifStatementString.append("else ");
        }
        dumpLabels(ifStatementString, expressionText, labels);
        dumpBody(ifStatementString, bodyStatements, renameBreaks, breakLabel,
                 variableDecls);
    }

    private static void dumpDefaultBranch(StringBuffer ifStatementString,
                                          List bodyStatements,
                                          boolean firstBranch,
                                          boolean renameBreaks,
                                          String breakLabel,
                                          Set variableDecls){
        if(!firstBranch){
            ifStatementString.append("else ");
        }
        dumpBody(ifStatementString, bodyStatements, renameBreaks, breakLabel,
                 variableDecls);
    }

    private static void dumpLabels(StringBuffer ifStatementString,
                                   String expressionText, List labels){
        ifStatementString.append("if(");
        boolean firstLabel = true;
        for(Iterator iterator = labels.iterator(); iterator.hasNext();){
            if(!firstLabel){
                ifStatementString.append("||");
            }
            firstLabel = false;
            final String valueText = (String) iterator.next();
            ifStatementString.append(expressionText);
            ifStatementString.append("==");
            ifStatementString.append(valueText);
        }
        ifStatementString.append(')');
    }

    private static void dumpBody(StringBuffer ifStatementString,
                                 List bodyStatements, boolean renameBreaks,
                                 String breakLabel, Set variableDecls){

        ifStatementString.append('{');
        for(Iterator iterator = variableDecls.iterator(); iterator.hasNext();){
            final PsiLocalVariable var = (PsiLocalVariable) iterator.next();
            if(CaseUtil.isUsedByStatementList(var, bodyStatements)){
                final PsiType varType = var.getType();
                ifStatementString.append(varType.getPresentableText() + ' ' +
                                                 var.getName() + ';');
            }
        }

        for(Iterator iterator = bodyStatements.iterator(); iterator.hasNext();){
            final PsiElement bodyStatement = (PsiElement) iterator.next();
            final String text = bodyStatement.getText();
            if(!"break;".equals(text)){
                appendElement(ifStatementString, bodyStatement, renameBreaks,
                              breakLabel);
            }
        }
        ifStatementString.append('}');
    }

    private static void appendElement(StringBuffer ifStatementString,
                                      PsiElement element,
                                      boolean renameBreakElements,
                                      String breakLabelString){
        if(!renameBreakElements){
            final String text = element.getText();
            ifStatementString.append(text);
        } else if(element instanceof PsiBreakStatement){
            final PsiIdentifier identifier =
                    ((PsiBreakStatement) element).getLabelIdentifier();
            if(identifier == null || "".equals(identifier.getText())){
                ifStatementString.append("break " + breakLabelString + ';');
            } else{
                final String text = element.getText();
                ifStatementString.append(text);
            }
        } else if(element instanceof PsiBlockStatement ||
                        element instanceof PsiCodeBlock ||
                        element instanceof PsiIfStatement){
            final PsiElement[] children = element.getChildren();
            for(int i = 0; i < children.length; i++){
                final PsiElement child = children[i];
                appendElement(ifStatementString, child, renameBreakElements,
                              breakLabelString);
            }
        } else{
            final String text = element.getText();
            ifStatementString.append(text);
        }
    }
}
