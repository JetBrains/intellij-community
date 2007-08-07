package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

class ExplicitClosureCallPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrMethodCallExpression)) {
            return false;
        }
        final GrMethodCallExpression call = (GrMethodCallExpression) element;
        final GrExpression invokedExpression = call.getInvokedExpression();
        if (invokedExpression == null) {
            return false;
        }
        if (!(invokedExpression instanceof GrReferenceExpression)) {
            return false;
        }
        final GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
        final String name = referenceExpression.getReferenceName();
        if (!"call".equals(name)) {
            return false;
        }
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType == null) {
            return false;
        }
        if (!qualifierType.equalsToText("groovy.lang.Closure")) {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
