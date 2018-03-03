package ru.adelf.idea.dotenv.go;

import com.goide.psi.GoCallExpr;
import com.goide.psi.GoStringLiteral;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class GoEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof GoCallExpr) {
            this.visitCall((GoCallExpr) element);
        }

        super.visitElement(element);
    }

    private void visitCall(GoCallExpr expression) {
        if(GoPsiHelper.checkEnvMethodCall(expression)
                && expression.getArgumentList().getExpressionList().size() > 0
                && expression.getArgumentList().getExpressionList().get(0) instanceof GoStringLiteral) {

            GoStringLiteral stringLiteral = (GoStringLiteral) expression.getArgumentList().getExpressionList().get(0);

            collectedItems.add(new KeyUsagePsiElement(stringLiteral.getDecodedText(), stringLiteral));
        }
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
