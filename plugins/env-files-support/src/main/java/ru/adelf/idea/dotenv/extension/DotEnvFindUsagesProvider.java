package ru.adelf.idea.dotenv.extension;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.grammars.DotEnvLexerAdapter;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;

public class DotEnvFindUsagesProvider implements FindUsagesProvider {
    @Nullable
    @Override
    public WordsScanner getWordsScanner() {
        return null;/*new DefaultWordsScanner(new DotEnvLexerAdapter(),
                TokenSet.create(DotEnvTypes.KEY),
                TokenSet.create(DotEnvTypes.COMMENT),
                TokenSet.EMPTY);*/
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return true;//psiElement instanceof PsiNamedElement;
    }

    @Nullable
    @Override
    public String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @NotNull
    @Override
    public String getType(@NotNull PsiElement element) {
//        if (element instanceof SimpleProperty) {
//            return "simple property";
//        } else {
            return "";
//        }
    }

    @NotNull
    @Override
    public String getDescriptiveName(@NotNull PsiElement element) {
//        if (element instanceof SimpleProperty) {
//            return ((SimpleProperty) element).getKey();
//        } else {
//            return "";
//        }
        return "";
    }

    @NotNull
    @Override
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
//        if (element instanceof SimpleProperty) {
//            return ((SimpleProperty) element).getKey() + ":" + ((SimpleProperty) element).getValue();
//        } else {
//            return "";
//        }

        return "";
    }
}