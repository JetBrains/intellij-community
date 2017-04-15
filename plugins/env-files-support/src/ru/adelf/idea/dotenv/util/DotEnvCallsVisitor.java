package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DotEnvCallsVisitor extends PsiRecursiveElementVisitor {
    final private Map<String, Set<PsiElement>> collectedKeys = new HashMap<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof StringLiteralExpression) {
            this.visitString((StringLiteralExpression) element);
        }

        super.visitElement(element);
    }

    private void visitString(StringLiteralExpression expression) {

        if(!PsiUtil.isEnvFunctionCall(expression)) return;

        String key = expression.getContents();

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
