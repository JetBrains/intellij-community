package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class ConvertStringToGStringIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new StringLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final GrLiteral exp = (GrLiteral) element;
        final String textString = exp.getText();

        replaceExpression(convertStringLiteralToGStringLiteral(textString), exp);
    }

    private static String convertStringLiteralToGStringLiteral(String stringLiteral) {
        final String delimiter;
        final String contents;
        if (stringLiteral.startsWith("'''")) {
            delimiter = "\"\"\"";
            contents = stringLiteral.substring(3, stringLiteral.length() - 3);
        } else {
            delimiter = "\"";
            contents = stringLiteral.substring(1, stringLiteral.length() - 1);
        }
        return delimiter + escape(contents) + delimiter;
    }

    private static String escape(String contents) {
        final StringBuilder out = new StringBuilder();
        final char[] chars = contents.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\"') {
                if (i > 0 && chars[i - 1] == '\\') {
                    out.append('"');
                } else {
                    out.append("\\\"");
                }
            } else if (chars[i] == '$') {
                if (i > 0 && chars[i - 1] == '\\') {
                    out.append('$');
                } else {
                    out.append("\\$");
                }
            } else {
                out.append(chars[i]);
            }

        }
        return out.toString();
    }
}
