package ru.adelf.idea.dotenv.models;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;

/**
 * Environment key and value PsiElement representation with key and value values.
 */
public class KeyValuePsiElement {

    private final String key;
    private final String value;
    private final PsiElement element;

    public KeyValuePsiElement(String key, String value, PsiElement element) {
        this.key = key;
        this.value = value;
        this.element = element;
    }

    public String getKey() {
        return key.trim();
    }

    public String getShortValue() {
        if (value.indexOf('\n') != -1) {
            return StringUtil.trimLeading(value.substring(0, value.indexOf('\n')), '"').trim() + "...";
        }

        return StringUtil.trimEnd(StringUtil.trimLeading(value, '"'), '"').trim();
    }

    public PsiElement getElement() {
        return element;
    }
}
