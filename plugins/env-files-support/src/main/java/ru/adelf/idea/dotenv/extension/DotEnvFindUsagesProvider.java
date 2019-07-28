package ru.adelf.idea.dotenv.extension;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.grammars.DotEnvLexerAdapter;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;

public class DotEnvFindUsagesProvider implements FindUsagesProvider {
    @Nullable
    @Override
    public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new DotEnvLexerAdapter(),
                TokenSet.create(DotEnvTypes.PROPERTY),
                TokenSet.create(DotEnvTypes.COMMENT),
                TokenSet.EMPTY);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiNamedElement;
    }

    @Nullable
    @Override
    public String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @NotNull
    @Override
    public String getType(@NotNull PsiElement element) {
        if (element instanceof DotEnvProperty) {
            return "Environment variable";
        } else {
            return "";
        }
    }

    @NotNull
    @Override
    public String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof DotEnvProperty) {
            return ((DotEnvProperty) element).getKeyText();
        } else {
            return "";
        }
    }

    @NotNull
    @Override
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        if (element instanceof DotEnvProperty) {
            return ((DotEnvProperty) element).getKeyText() + ":" + ((DotEnvProperty) element).getValueText();
        } else {
            return "";
        }
    }
}