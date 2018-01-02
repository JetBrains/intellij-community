package ru.adelf.idea.dotenv.js;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;

class JsEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {

        if(JsPsiHelper.checkPsiElement(element)) {
            collectedItems.add(new KeyUsagePsiElement(element.getText(), element));
        }

        super.visitElement(element);
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
