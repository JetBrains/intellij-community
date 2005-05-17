package com.siyeh.ig.confusing;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
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

    public BaseInspectionVisitor buildVisitor(){
        return new SwitchStatementWithConfusingDeclarationVisitor();
    }

    private static class SwitchStatementWithConfusingDeclarationVisitor
            extends StatementInspectionVisitor{


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
