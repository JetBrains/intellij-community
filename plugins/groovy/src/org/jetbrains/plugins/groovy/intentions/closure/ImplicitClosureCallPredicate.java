package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

class ImplicitClosureCallPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrMethodCallExpression)) {
            return false;
        }
        final GrMethodCallExpression call = (GrMethodCallExpression) element;
        final GrExpression invokedExpression = call.getInvokedExpression();
        if (invokedExpression == null) {
            return false;
        }
        final PsiType type = invokedExpression.getType();
        if(type == null)
        {
            return false;
        }
        if (!type.equalsToText("groovy.lang.Closure")) {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
