package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SwitchStatementWithConfusingDeclarationInspection
        extends StatementInspection{
    public String getID(){
        return "LocalVariableUsedAndDeclaredInDifferentSwitchBranches";
    }

    public String getDisplayName(){
        return "Local variable used and declared in different 'switch' branches";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location){
        return "Local variable #ref declared in one switch branch and used in another #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new SwitchStatementWithConfusingDeclarationVisitor(this,
                                                                  inspectionManager,
                                                                  onTheFly);
    }

    private static class SwitchStatementWithConfusingDeclarationVisitor
            extends BaseInspectionVisitor{
        private SwitchStatementWithConfusingDeclarationVisitor(BaseInspection inspection,
                                                               InspectionManager inspectionManager,
                                                               boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement){
            final Set variablesInCurrentBranch = new HashSet(10);
            final Set variablesInPreviousBranches = new HashSet(10);
            final PsiCodeBlock body = statement.getBody();
            if(body == null){
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for(int i = 0; i < statements.length; i++){
                final PsiStatement child = statements[i];
                if(child instanceof PsiDeclarationStatement){
                    final PsiDeclarationStatement declaration =
                            (PsiDeclarationStatement) child;
                    final PsiElement[] declaredElements =
                            declaration.getDeclaredElements();
                    for(int j = 0; j < declaredElements.length; j++){
                        final PsiElement declaredElement = declaredElements[j];
                        if(declaredElement instanceof PsiLocalVariable){
                            final PsiLocalVariable localVar =
                                    (PsiLocalVariable) declaredElement;
                            variablesInCurrentBranch.add(localVar);
                        }
                    }
                }
                if(child instanceof PsiBreakStatement){
                    variablesInPreviousBranches.addAll(variablesInCurrentBranch);
                    variablesInCurrentBranch.clear();
                }
                final LocalVariableAccessVisitor visitor =
                        new LocalVariableAccessVisitor();
                child.accept(visitor);
                final Set accessedVariables = visitor.getAccessedVariables();
                for(Iterator iterator = accessedVariables.iterator();
                    iterator.hasNext();){
                    final PsiLocalVariable localVar =
                            (PsiLocalVariable) iterator.next();
                    if(variablesInPreviousBranches.contains(localVar)){
                        variablesInPreviousBranches.remove(localVar);
                        registerVariableError(localVar);
                    }
                }
            }
        }
    }
}
