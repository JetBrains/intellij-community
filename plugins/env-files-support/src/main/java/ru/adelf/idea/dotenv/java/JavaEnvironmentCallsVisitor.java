package ru.adelf.idea.dotenv.java;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class JavaEnvironmentCallsVisitor extends JavaRecursiveElementWalkingVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (JavaPsiHelper.isEnvMethodCall(expression)) {
            PsiElement[] parameters = expression.getArgumentList().getExpressions();

            if (parameters.length == 0) return;

            if (!(parameters[0] instanceof PsiLiteralExpression)) return;

            Object value = ((PsiLiteralExpression) parameters[0]).getValue();

            if (value instanceof String) {
                collectedItems.add(new KeyUsagePsiElement((String) value, parameters[0]));
            }
        }

        super.visitMethodCallExpression(expression);
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
