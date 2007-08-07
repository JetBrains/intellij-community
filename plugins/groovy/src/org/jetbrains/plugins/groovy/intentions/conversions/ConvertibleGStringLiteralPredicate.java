package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;


class ConvertibleGStringLiteralPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrLiteral)) {
            return false;
        }

        @NonNls final String text = element.getText();

        return text.charAt(0) == '"' && !text.contains("$");
    }
}
