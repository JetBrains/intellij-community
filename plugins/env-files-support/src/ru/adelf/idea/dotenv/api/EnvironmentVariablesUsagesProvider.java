package ru.adelf.idea.dotenv.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public interface EnvironmentVariablesUsagesProvider {
    boolean acceptFile(VirtualFile file);

    @NotNull
    Collection<String> getKeys(FileContent fileContent);

    @NotNull
    Set<PsiElement> getTargetsByKey(String key, PsiFile psiFile);
}
