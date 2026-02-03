package ru.adelf.idea.dotenv.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

import java.util.Collection;

public interface EnvironmentVariablesProvider {
    @NotNull
    FileAcceptResult acceptFile(VirtualFile file);

    @NotNull
    Collection<KeyValuePsiElement> getElements(PsiFile psiFile);
}
