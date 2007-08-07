package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;

public class IndexedExpressionConversionIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new IndexedExpressionConversionPredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {

        final GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;

        final GrArgumentList argList = (GrArgumentList) arrayIndexExpression.getLastChild();

        assert argList != null;
        final GrExpression[] arguments = argList.getExpressionArguments();

        final PsiElement parent = element.getParent();
        final GrExpression arrayExpression = arrayIndexExpression.getArrayExpression();
        if (!(parent instanceof GrAssignmentExpression)) {
            rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
            return;
        }
        final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
        final GrExpression rhs = assignmentExpression.getRValue();
        if (rhs.equals(element)) {
            rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
        } else {
            rewriteAsSetAt(assignmentExpression, arrayExpression, arguments[0], rhs);
        }
    }

    private static void rewriteAsGetAt(GrIndexProperty arrayIndexExpression, GrExpression arrayExpression, GrExpression argument) throws IncorrectOperationException {
        replaceExpression(arrayExpression.getText() + ".getAt(" + argument.getText() + ')', arrayIndexExpression);
    }

    private static void rewriteAsSetAt(GrAssignmentExpression assignment, GrExpression arrayExpression, GrExpression argument, GrExpression value) throws IncorrectOperationException {
        replaceExpression(arrayExpression.getText() + ".setAt(" + argument.getText() + ", " + value.getText() + ')', assignment);
    }

}
