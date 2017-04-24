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
import java.util.Set;

public class DockerfileVariablesProvider implements EnvVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(DockerFileType.DOCKER_FILE_TYPE);
    }

    @NotNull
    @Override
    public Collection<Pair<String, String>> getKeyValues(FileContent fileContent) {
        return getVisitorForFile(fileContent.getPsiFile()).getKeyValues();
    }

    @NotNull
    @Override
    public Set<PsiElement> getTargetsByKey(String key, PsiFile psiFile) {
        return getVisitorForFile(psiFile).getElementsByKey(key);
    }

    private DockerfilePsiElementsVisitor getVisitorForFile(PsiFile psiFile) {
        DockerfilePsiElementsVisitor visitor = new DockerfilePsiElementsVisitor();

        if(psiFile instanceof DockerPsiFile) {
            psiFile.acceptChildren(visitor);
        }

        return visitor;
    }
}
