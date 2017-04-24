package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DotEnvPsiElementsVisitor extends PsiRecursiveElementVisitor {
    final private Set<DotEnvProperty> collectedProperties = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof DotEnvProperty) {
            this.visitProperty((DotEnvProperty) element);
        }

        super.visitElement(element);
    }

    private void visitProperty(DotEnvProperty property) {
        collectedProperties.add(property);
    }

    @NotNull
    public Collection<Pair<String, String>> getKeyValues() {
        return this.collectedProperties.stream().map(property -> {
            String text = property.getText();
            int pos = text.indexOf("=");

            if(pos == -1) {
                return new Pair<>(text.trim(), "");
            } else {
                return new Pair<>(text.substring(0, pos).trim(), text.substring(pos + 1).trim());
            }
        }).collect(Collectors.toList());
    }

    @NotNull
    public Set<PsiElement> getElementsByKey(String key) {
        return this.collectedProperties.stream().filter(property -> {
            String[] splitParts = property.getText().split("=", -1);

            return splitParts[0].trim().equals(key);
        }).collect(Collectors.toSet());
    }
}
