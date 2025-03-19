package ru.adelf.idea.dotenv.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public abstract class DotEnvNamedElementImpl extends ASTWrapperPsiElement implements DotEnvNamedElement {
    public DotEnvNamedElementImpl(@NotNull ASTNode node) {
        super(node);
    }
}
