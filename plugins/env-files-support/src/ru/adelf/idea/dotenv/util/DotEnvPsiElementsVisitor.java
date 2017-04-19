package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;

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
        String text = property.getText();
        String[] splitParts = text.split("=", -1);

        if(splitParts.length < 2) return;

        String key = splitParts[0].trim();

        if("".equals(key) || key.charAt(0) == '#') return;

        collectedProperties.add(property);
    }

    @NotNull
    public Set<String> getKeys() {
        return this.collectedProperties.stream()
                .map(property -> property.getText().split("=", -1)[0].trim())
                .collect(Collectors.toSet());
    }

    @NotNull
    public Set<String> getKeyValues() {
        return this.collectedProperties.stream().map(PsiElement::getText).collect(Collectors.toSet());
    }

    @NotNull
    public DotEnvProperty[] getElementsByKey(String key) {
        Set<DotEnvProperty> targets = this.collectedProperties.stream().filter(property -> {
            String[] splitParts = property.getText().split("=", -1);

            return splitParts[0].trim().equals(key);
        }).collect(Collectors.toSet());

        return targets.toArray(new DotEnvProperty[0]);
    }
}
