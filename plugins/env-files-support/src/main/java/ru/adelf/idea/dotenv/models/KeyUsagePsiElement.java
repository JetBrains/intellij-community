package ru.adelf.idea.dotenv.models;

import com.intellij.psi.PsiElement;

/**
 * Environment key usage PsiElement representation with key
 */
public class KeyUsagePsiElement {

    private final String key;
    private final PsiElement element;

    public KeyUsagePsiElement(String key, PsiElement element) {
        this.key = key;
        this.element = element;
    }

    public String getKey() {
        return key;
    }

    public PsiElement getElement() {
        return element;
    }
}
