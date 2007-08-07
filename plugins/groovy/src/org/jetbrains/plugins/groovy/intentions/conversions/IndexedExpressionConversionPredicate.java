package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;


class IndexedExpressionConversionPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrIndexProperty)) {
            return false;
        }

        if (ErrorUtil.containsError(element)) {
            return false;
        }
        final GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;
        final PsiElement lastChild = arrayIndexExpression.getLastChild();
        if (!(lastChild instanceof GrArgumentList)) {
            return false;
        }
        final GrArgumentList argList = (GrArgumentList) lastChild;

        final GrExpression[] arguments = argList.getExpressionArguments();
        if (arguments.length != 1) {
            return false;
        }
        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrAssignmentExpression)) {
            return true;
        }
        final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
        if (assignmentExpression.getRValue().equals(element)) {
            return true;
        }
        final IElementType operator = assignmentExpression.getOperationToken();
        return GroovyTokenTypes.mASSIGN.equals(operator);
    }

}
