package ru.adelf.idea.dotenv.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;

public interface EnvironmentVariablesUsagesProvider {
    boolean acceptFile(VirtualFile file);

    @NotNull
    Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile);
}
