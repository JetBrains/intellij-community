package ru.adelf.idea.dotenv.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.FileContent;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface EnvVariablesProvider {
    boolean acceptFile(VirtualFile file);

    @NotNull
    Collection<Pair<String, String>> getKeyValues(FileContent fileContent);

    @NotNull
    PsiElement[] getTargetsByKey(String key, FileContent fileContent);
}
