package ru.adelf.idea.dotenv.extension;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.psi.DotEnvValue;

public class DotEnvValuesHiding extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                          @NotNull Document document,
                                                          boolean quick) {
        if (!DotEnvSettings.getInstance().hideValuesInTheFile) return emptyResult;

        return PsiTreeUtil.collectElementsOfType(root, DotEnvValue.class).stream().map(
                dotEnvValue -> new FoldingDescriptor(
                        dotEnvValue.getNode(),
                        dotEnvValue.getTextRange(),
                        null,
                        "Show value"
                )
        ).toArray(FoldingDescriptor[]::new);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }

    private static final FoldingDescriptor[] emptyResult = new FoldingDescriptor[0];
}
