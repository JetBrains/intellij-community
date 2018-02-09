package ru.adelf.idea.dotenv;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.api.FileAcceptResult;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvFile;

import java.util.Collection;
import java.util.Collections;

public class DotEnvVariablesProvider implements EnvironmentVariablesProvider {
    @NotNull
    @Override
    public FileAcceptResult acceptFile(VirtualFile file) {
        if(!file.getFileType().equals(DotEnvFileType.INSTANCE)) {
            return FileAcceptResult.NOT_ACCEPTED;
        }

        // .env.dist , .env.example files are secondary
        return file.getName().equals(".env") ? FileAcceptResult.ACCEPTED : FileAcceptResult.ACCEPTED_SECONDARY;
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
