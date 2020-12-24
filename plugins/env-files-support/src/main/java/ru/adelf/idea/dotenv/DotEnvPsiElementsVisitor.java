package ru.adelf.idea.dotenv;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;

import java.util.Collection;
import java.util.HashSet;

public class DotEnvPsiElementsVisitor extends PsiRecursiveElementVisitor {
    private final Collection<KeyValuePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if(element instanceof DotEnvProperty) {
            this.visitProperty((DotEnvProperty) element);
        }

        super.visitElement(element);
    }

    private void visitProperty(DotEnvProperty property) {
        collectedItems.add(new KeyValuePsiElement(
                property.getKeyText(),
                property.getValueText(),
                property));
    }

    public Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
