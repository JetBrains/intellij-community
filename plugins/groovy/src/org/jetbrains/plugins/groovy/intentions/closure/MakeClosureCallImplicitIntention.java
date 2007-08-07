package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class MakeClosureCallImplicitIntention extends Intention {


    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExplicitClosureCallPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final GrMethodCallExpression expression =
                (GrMethodCallExpression) element;
        final GrReferenceExpression invokedExpression = (GrReferenceExpression) expression.getInvokedExpression();
        final GrExpression qualifier = invokedExpression.getQualifierExpression();
        final GrArgumentList argList = expression.getArgumentList();
        final GrClosableBlock[] closureArgs = expression.getClosureArguments();
        final StringBuilder newExpression = new StringBuilder();
        newExpression.append(qualifier.getText());
        if (argList != null) {
            newExpression.append(argList.getText());
        }
        for (GrClosableBlock closureArg : closureArgs) {
            newExpression.append(closureArg.getText());
        }
        replaceExpression(newExpression.toString(), expression);
    }
}
