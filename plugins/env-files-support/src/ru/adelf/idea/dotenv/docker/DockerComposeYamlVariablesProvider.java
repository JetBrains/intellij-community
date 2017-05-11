package ru.adelf.idea.dotenv.docker;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class DockerComposeYamlVariablesProvider implements EnvironmentVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        if(file.getName().equals("docker-compose.yml") || file.getName().equals("docker-compose.yaml")) {
            return file.getFileType().equals(YAMLFileType.YML);
        }

        return false;
    }

    @NotNull
    @Override
    public Collection<KeyValuePsiElement> getElements(PsiFile psiFile) {

        if(psiFile instanceof YAMLFile) {
            DockerComposeYamlPsiElementsVisitor visitor = new DockerComposeYamlPsiElementsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
