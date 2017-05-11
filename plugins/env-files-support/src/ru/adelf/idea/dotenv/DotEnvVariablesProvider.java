package ru.adelf.idea.dotenv;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvFile;

import java.util.Collection;
import java.util.Collections;

public class DotEnvVariablesProvider implements EnvironmentVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(DotEnvFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<KeyValuePsiElement> getElements(PsiFile psiFile) {
        if(psiFile instanceof DotEnvFile) {
            DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
