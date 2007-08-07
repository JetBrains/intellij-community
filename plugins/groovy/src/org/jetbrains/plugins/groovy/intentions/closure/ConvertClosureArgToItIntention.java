package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;

import java.util.HashSet;
import java.util.Set;

public class ConvertClosureArgToItIntention extends Intention {


    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new SingleArgClosurePredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final GrClosableBlock closure =
                (GrClosableBlock) element;

        final GrParameterList parameterList = closure.getParameterList();
        final GrParameter parameter = parameterList.getParameters()[0];
        final Set<GrReferenceExpression> referencesToChange = new HashSet<GrReferenceExpression>();
        final GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
            public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
                super.visitReferenceExpression(referenceExpression);
                if (!referenceExpression.getText().equals(parameter.getName())) {
                    return;
                }
                final PsiElement referent = referenceExpression.resolve();
                if (parameter.equals(referent)) {
                    referencesToChange.add(referenceExpression);
                }
            }
        };
        closure.accept(visitor);
        parameter.delete();
        for (GrReferenceExpression referenceExpression : referencesToChange) {
            replaceExpression("it", referenceExpression );
        }
    }

}
