package ru.adelf.idea.dotenv.docker;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.api.FileAcceptResult;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

import java.util.Collection;
import java.util.Collections;

public class DockerComposeYamlVariablesProvider implements EnvironmentVariablesProvider {
    @NotNull
    @Override
    public FileAcceptResult acceptFile(VirtualFile file) {
        if (file.getName().equals("docker-compose.yml") || file.getName().equals("docker-compose.yaml")) {
            return file.getFileType().equals(YAMLFileType.YML) ? FileAcceptResult.ACCEPTED : FileAcceptResult.NOT_ACCEPTED;
        }

        return FileAcceptResult.NOT_ACCEPTED;
    }

    @NotNull
    @Override
    public Collection<KeyValuePsiElement> getElements(PsiFile psiFile) {
        if (psiFile instanceof YAMLFile) {
            DockerComposeYamlPsiElementsVisitor visitor = new DockerComposeYamlPsiElementsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
