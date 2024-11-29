package ru.adelf.idea.dotenv.php;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class PhpEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if(element instanceof FunctionReference) {
            this.visitFunction((FunctionReference) element);
        }

        if(element instanceof ArrayAccessExpression) {
            this.visitArrayAccess((ArrayAccessExpression) element);
        }

        super.visitElement(element);
    }

    private void visitFunction(FunctionReference expression) {

        if(!PhpPsiHelper.isEnvFunction(expression)) return;

        PsiElement[] parameters = expression.getParameters();

        if(parameters.length == 0) return;

        if(!(parameters[0] instanceof StringLiteralExpression)) return;

        String key = ((StringLiteralExpression)parameters[0]).getContents();

        collectedItems.add(new KeyUsagePsiElement(key, parameters[0]));
    }

    private void visitArrayAccess(ArrayAccessExpression expression) {
        if(!PhpPsiHelper.isEnvArrayCall(expression)) return;

        if(expression.getIndex() == null) return;

        PsiElement indexLiteral = expression.getIndex().getValue();

        if(!(indexLiteral instanceof StringLiteralExpression)) return;

        String key = ((StringLiteralExpression)indexLiteral).getContents();

        collectedItems.add(new KeyUsagePsiElement(key, indexLiteral));
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
