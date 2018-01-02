package ru.adelf.idea.dotenv.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class PythonEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof PyCallExpression) {
            this.visitCall((PyCallExpression) element);
        }

        if(element instanceof PySubscriptionExpression) {
            this.visitIndex((PySubscriptionExpression) element);
        }

        super.visitElement(element);
    }

    private void visitCall(PyCallExpression expression) {
        if(PythonPsiHelper.checkGetMethodCall(expression)
                && expression.getArgumentList() != null
                && expression.getArgumentList().getArguments().length > 0
                && expression.getArgumentList().getArguments()[0] instanceof PyStringLiteralExpression) {
            PyStringLiteralExpression stringLiteral = (PyStringLiteralExpression) expression.getArgumentList().getArguments()[0];

            collectedItems.add(new KeyUsagePsiElement(stringLiteral.getStringValue(), stringLiteral));
        }
    }

    private void visitIndex(PySubscriptionExpression expression) {
        if(PythonPsiHelper.checkIndexCall(expression) &&
                expression.getIndexExpression() instanceof PyStringLiteralExpression) {

            PyStringLiteralExpression stringLiteral = (PyStringLiteralExpression) expression.getIndexExpression();

            collectedItems.add(new KeyUsagePsiElement(stringLiteral.getStringValue(), stringLiteral));
        }
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
