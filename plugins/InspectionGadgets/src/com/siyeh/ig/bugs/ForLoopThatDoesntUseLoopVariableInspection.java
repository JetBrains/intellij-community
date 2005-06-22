package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ForLoopThatDoesntUseLoopVariableInspection
        extends StatementInspection{
    public String getDisplayName(){
        return "'for' loop where update or condition doesn't use loop variable";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final List<String> compenentsMissing = new ArrayList<String>(3);
        final PsiJavaToken forToken = (PsiJavaToken) location;
        final PsiForStatement forStatement =
                (PsiForStatement) forToken.getParent();

        if(!conditionUsesInitializer(forStatement)){
            compenentsMissing.add("condition");
        }
        if(!updateUsesInitializer(forStatement)){
            compenentsMissing.add("update");
        }
        final String missingComponents;
        final String doString;
        if(compenentsMissing.size() == 1){
            missingComponents = compenentsMissing.get(0);
            doString = "does";
        } else{
            missingComponents = compenentsMissing.get(0) + " and " +
                    compenentsMissing.get(1);
            doString = "do";
        }
        return "#ref statement has " + missingComponents + " which " +
                       doString + " not use the for loop variable #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ForLoopThatDoesntUseLoopVariableVisitor();
    }

    private static class ForLoopThatDoesntUseLoopVariableVisitor
            extends StatementInspectionVisitor{


        public void visitForStatement(@NotNull PsiForStatement statement){
            super.visitForStatement(statement);

            if(conditionUsesInitializer(statement)
                       && updateUsesInitializer(statement)){
                return;
            }
            registerStatementError(statement);
        }
    }

    private static boolean conditionUsesInitializer(PsiForStatement statement){
        final PsiStatement initialization = statement.getInitialization();
        final PsiExpression condition = statement.getCondition();

        if(initialization == null){
            return true;
        }
        if(condition == null){
            return true;
        }
        if(!(initialization instanceof PsiDeclarationStatement)){
            return true;
        }
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;

        final PsiElement[] declaredElements = declaration.getDeclaredElements();

        if(declaredElements == null || declaredElements.length != 1){
            return true;
        }
        if(declaredElements[0] == null ||
                   !(declaredElements[0] instanceof PsiLocalVariable)){
            return true;
        }
        final PsiLocalVariable localVar =
                (PsiLocalVariable) declaredElements[0];
        return expressionUsesVariable(condition, localVar);
    }

    private static boolean updateUsesInitializer(PsiForStatement statement){
        final PsiStatement initialization = statement.getInitialization();
        final PsiStatement update = statement.getUpdate();

        if(initialization == null){
            return true;
        }
        if(update == null){
            return true;
        }
        if(!(initialization instanceof PsiDeclarationStatement)){
            return true;
        }
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;

        final PsiElement[] declaredElements = declaration.getDeclaredElements();

        if(declaredElements == null || declaredElements.length != 1){
            return true;
        }
        if(declaredElements[0] == null ||
                   !(declaredElements[0] instanceof PsiLocalVariable)){
            return true;
        }

        final PsiLocalVariable localVar =
                (PsiLocalVariable) declaredElements[0];
        return statementUsesVariable(update, localVar);
    }

    private static boolean statementUsesVariable(PsiStatement statement,
                                                 PsiLocalVariable localVar){
        final UseVisitor useVisitor = new UseVisitor(localVar);
        statement.accept(useVisitor);
        return useVisitor.isUsed();
    }

    private static boolean expressionUsesVariable(PsiExpression expression,
                                                  PsiLocalVariable localVar){
        final UseVisitor useVisitor = new UseVisitor(localVar);
        expression.accept(useVisitor);
        return useVisitor.isUsed();
    }

    private static class UseVisitor extends PsiRecursiveElementVisitor{
        private final PsiLocalVariable variable;
        private boolean used = false;

        private UseVisitor(PsiLocalVariable var){
            super();
            variable = var;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!used){
                super.visitElement(element);
            }
        }

        public void visitReferenceExpression(@NotNull PsiReferenceExpression ref){
            if(used){
                return;
            }
            super.visitReferenceExpression(ref);
            final PsiElement resolvedElement = ref.resolve();
            if(variable.equals(resolvedElement)){
                used = true;
            }
        }

        private boolean isUsed(){
            return used;
        }
    }
}
