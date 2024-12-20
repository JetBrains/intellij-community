package ru.adelf.idea.dotenv.extension;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvBundle;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;

public class DotEnvFindUsagesProvider implements FindUsagesProvider {
    @Nullable
    @Override
    public WordsScanner getWordsScanner() {
        throw new UnsupportedOperationException("Not yet implemented");
        /*
         TODO commented out because `DefaultWordsScanner` requires intellij.platform.indexing.impl dependency
              but this class is unused
        return new DefaultWordsScanner(new DotEnvLexerAdapter(),
                TokenSet.create(DotEnvTypes.PROPERTY),
                TokenSet.create(DotEnvTypes.COMMENT),
                TokenSet.EMPTY);
        */
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
            return DotEnvBundle.message("environment.variable");
        } else {
            return "";
        }
    }

    @NotNull
    @Override
    public String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof DotEnvProperty property) {
            return property.getKeyText();
        } else {
            return "";
        }
    }

    @NotNull
    @Override
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        if (element instanceof DotEnvProperty property) {
            return property.getKeyText() + ":" + property.getValueText();
        } else {
            return "";
        }
    }
}