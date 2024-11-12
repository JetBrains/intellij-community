package ru.adelf.idea.dotenv.docker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.*;
import ru.adelf.idea.dotenv.models.EnvironmentKeyValue;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

class DockerComposeYamlPsiElementsVisitor extends PsiRecursiveElementVisitor {
    private final Collection<KeyValuePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof YAMLKeyValue) {
            this.visitKeyValue((YAMLKeyValue) element);
        }

        super.visitElement(element);
    }

    Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }

    private void visitKeyValue(YAMLKeyValue yamlKeyValue) {
        if ("environment".equals(yamlKeyValue.getKeyText())) {
            for (YAMLSequenceItem yamlSequenceItem : getSequenceItems(yamlKeyValue)) {
                YAMLValue el = yamlSequenceItem.getValue();
                if (el instanceof YAMLScalar) {
                    EnvironmentKeyValue keyValue = EnvironmentVariablesUtil.getKeyValueFromString(((YAMLScalar) el).getTextValue());

                    if (StringUtils.isNotBlank(keyValue.getKey())) {
                        collectedItems.add(new KeyValuePsiElement(keyValue.getKey(), keyValue.getValue(), el));
                    }
                }
            }

            for (YAMLKeyValue keyValue : getMappingItems(yamlKeyValue)) {
                collectedItems.add(new KeyValuePsiElement(keyValue.getKeyText(), keyValue.getValueText(), keyValue));
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

        if (yamlSequence instanceof YAMLSequence) {
            return ((YAMLSequence) yamlSequence).getItems();
        }

        return Collections.emptyList();
    }


    /**
     * FOO:
     * bar: true
     */
    @NotNull
    private Collection<YAMLKeyValue> getMappingItems(@NotNull YAMLKeyValue yamlKeyValue) {
        PsiElement yamlMapping = yamlKeyValue.getLastChild();

        if (yamlMapping instanceof YAMLMapping) {
            return ((YAMLMapping) yamlMapping).getKeyValues();
        }

        return Collections.emptyList();
    }
}
