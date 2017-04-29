package ru.adelf.idea.dotenv.docker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.*;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerComposeYamlPsiElementsVisitor  extends PsiRecursiveElementVisitor {
    private final Set<YAMLScalar> collectedDeclarations = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof YAMLKeyValue) {
            this.visitKeyValue((YAMLKeyValue) element);
        }

        super.visitElement(element);
    }

    private void visitKeyValue(YAMLKeyValue yamlKeyValue) {
        if("environment".equals(yamlKeyValue.getKeyText())) {
            for(YAMLSequenceItem yamlSequenceItem : getSequenceItems(yamlKeyValue)) {
                YAMLValue value = yamlSequenceItem.getValue();
                if(value instanceof YAMLScalar) {
                    String textValue = ((YAMLScalar) value).getTextValue();
                    if(StringUtils.isNotBlank(textValue)) {
                        String[] split = textValue.split("=");
                        if(split.length > 1) {
                            collectedDeclarations.add((YAMLScalar) value);
                        }
                    }
                }
            }
        }
    }

    /**
     * FOO:
     * - foobar
     * <p>
     * FOO: [foobar]
     */
    @NotNull
    private Collection<YAMLSequenceItem> getSequenceItems(@NotNull YAMLKeyValue yamlKeyValue) {
        PsiElement yamlSequence = yamlKeyValue.getLastChild();

        if(yamlSequence instanceof YAMLSequence) {
            return ((YAMLSequence) yamlSequence).getItems();
        }

        return Collections.emptyList();
    }

    @NotNull
    public Collection<Pair<String, String>> getKeyValues() {
        return this.collectedDeclarations.stream()
                .map(declaration -> EnvironmentVariablesUtil.getKeyValueFromString(declaration.getTextValue()))
                .filter(stringStringPair -> !StringUtils.isBlank(stringStringPair.getKey()))
                .collect(Collectors.toList());
    }

    @NotNull
    public Set<PsiElement> getElementsByKey(String key) {
        return this.collectedDeclarations.stream()
                .filter(declaration -> EnvironmentVariablesUtil.getKeyFromString(declaration.getTextValue()).equals(key))
                .collect(Collectors.toSet());
    }
}
