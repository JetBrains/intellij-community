package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

class SingleArgClosurePredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrClosableBlock)) {
            return false;
        }
        final GrClosableBlock closure = (GrClosableBlock) element;
        if(closure.getParameterList().getParametersCount() !=1)
        {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
