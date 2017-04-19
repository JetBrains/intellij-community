package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DotEnvCallsVisitor extends PsiRecursiveElementVisitor {
    final private Map<String, Set<PsiElement>> collectedKeys = new HashMap<>();

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

        if(collectedKeys.containsKey(key)) {
            collectedKeys.get(key).add(expression);
        } else {
            collectedKeys.put(key, new HashSet<>(Collections.singletonList(expression)));
        }
    }

    @NotNull
    public Set<String> getKeys() {
        return collectedKeys.keySet();
    }

    @NotNull
    public PsiElement[] getTargets(String key) {
        Set<PsiElement> psiElements = collectedKeys.get(key);

        if(psiElements == null) return new PsiElement[0];

        return psiElements.toArray(new PsiElement[0]);
    }
}
