package ru.adelf.idea.dotenv.docker;

import com.intellij.docker.dockerFile.DockerPsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.docker.dockerFile.DockerFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvVariablesProvider;

import java.util.Collection;

public class DockerfileVariablesProvider implements EnvVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType() instanceof DockerFileType;
    }

    @NotNull
    @Override
    public Collection<Pair<String, String>> getKeyValues(FileContent fileContent) {
        return getVisitorForFile(fileContent).getKeyValues();
    }

    @NotNull
    @Override
    public PsiElement[] getTargetsByKey(String key, FileContent fileContent) {
        return getVisitorForFile(fileContent).getElementsByKey(key);
    }

    private DockerfilePsiElementsVisitor getVisitorForFile(FileContent fileContent) {
        DockerfilePsiElementsVisitor visitor = new DockerfilePsiElementsVisitor();

        PsiFile psiFile = fileContent.getPsiFile();

        if(psiFile instanceof DockerPsiFile) {
            psiFile.acceptChildren(visitor);
        }

        return visitor;
    }
}
