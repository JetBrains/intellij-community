package ru.adelf.idea.dotenv.php;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;
import ru.adelf.idea.dotenv.util.PsiUtil;

import java.util.*;

class PhpEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof FunctionReference) {
            this.visitFunction((FunctionReference) element);
        }

        super.visitElement(element);
    }

    private void visitFunction(FunctionReference expression) {

        if(!PsiUtil.isEnvFunction(expression)) return;

        PsiElement[] parameters = expression.getParameters();

        if(parameters.length == 0) return;

        if(!(parameters[0] instanceof StringLiteralExpression)) return;

        String key = ((StringLiteralExpression)parameters[0]).getContents();

        collectedItems.add(new KeyUsagePsiElement(key, parameters[0]));
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
