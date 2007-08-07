package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class MakeClosureCallExplicitIntention extends Intention {


    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ImplicitClosureCallPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final GrMethodCallExpression expression =
                (GrMethodCallExpression) element;
        final GrExpression invokedExpression = expression.getInvokedExpression();
        final GrArgumentList argList = expression.getArgumentList();
        final GrClosableBlock[] closureArgs = expression.getClosureArguments();
        final StringBuilder newExpression = new StringBuilder();
        newExpression.append(invokedExpression.getText());
        newExpression.append(".call");
        if (argList != null) {
            newExpression.append(argList.getText());
        }
        for (GrClosableBlock closureArg : closureArgs) {
            newExpression.append(closureArg.getText());
        }
        replaceExpression(newExpression.toString(), expression);
    }
}
