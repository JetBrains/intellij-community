package ru.adelf.idea.dotenv;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import ru.adelf.idea.dotenv.models.EnvironmentKeyValue;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.Collection;
import java.util.HashSet;

class DotEnvPsiElementsVisitor extends PsiRecursiveElementVisitor {
    private final Collection<KeyValuePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof DotEnvProperty) {
            this.visitProperty((DotEnvProperty) element);
        }

        super.visitElement(element);
    }

    private void visitProperty(DotEnvProperty property) {
        EnvironmentKeyValue keyValue = EnvironmentVariablesUtil.getKeyValueFromString(property.getText());

        collectedItems.add(new KeyValuePsiElement(keyValue.getKey(), keyValue.getValue(), property));
    }

    Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
