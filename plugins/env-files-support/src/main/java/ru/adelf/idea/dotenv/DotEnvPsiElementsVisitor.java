package ru.adelf.idea.dotenv;

import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.psi.DotEnvVisitor;

import java.util.Collection;
import java.util.HashSet;

public class DotEnvPsiElementsVisitor extends DotEnvVisitor {
    private final Collection<KeyValuePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitProperty(@NotNull DotEnvProperty property) {
        collectedItems.add(new KeyValuePsiElement(
            property.getKeyText(),
            property.getValueText(),
            property)
        );
    }

    public Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
