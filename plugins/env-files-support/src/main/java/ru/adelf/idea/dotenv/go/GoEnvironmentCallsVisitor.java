package ru.adelf.idea.dotenv.go;

import com.goide.psi.GoCallExpr;
import com.goide.psi.GoStringLiteral;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class GoEnvironmentCallsVisitor extends PsiRecursiveElementWalkingVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof GoCallExpr) {
            this.visitCall((GoCallExpr) element);
        }

        super.visitElement(element);
    }

    private void visitCall(GoCallExpr expression) {
        GoStringLiteral stringLiteral = GoPsiHelper.getEnvironmentGoLiteral(expression);
        if(stringLiteral != null) {
            collectedItems.add(new KeyUsagePsiElement(stringLiteral.getDecodedText(), stringLiteral));
        }
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
