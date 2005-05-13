package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
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
            extends StatementInspectionVisitor{
        private SwitchStatementWithConfusingDeclarationVisitor(BaseInspection inspection,
                                                               InspectionManager inspectionManager,
                                                               boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement){
            final Set<PsiLocalVariable> variablesInCurrentBranch = new HashSet<PsiLocalVariable>(10);
            final Set<PsiLocalVariable> variablesInPreviousBranches = new HashSet<PsiLocalVariable>(10);
            final PsiCodeBlock body = statement.getBody();
            if(body == null){
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for(final PsiStatement child : statements){
                if(child instanceof PsiDeclarationStatement){
                    final PsiDeclarationStatement declaration =
                            (PsiDeclarationStatement) child;
                    final PsiElement[] declaredElements =
                            declaration.getDeclaredElements();
                    for(final PsiElement declaredElement : declaredElements){
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
                final Set<PsiElement> accessedVariables = visitor.getAccessedVariables();
                for(Object accessedVariable : accessedVariables){
                    final PsiLocalVariable localVar =
                            (PsiLocalVariable) accessedVariable;
                    if(variablesInPreviousBranches.contains(localVar)){
                        variablesInPreviousBranches.remove(localVar);
                        registerVariableError(localVar);
                    }
                }
            }
        }
    }
}
