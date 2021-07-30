package ru.adelf.idea.dotenv.docker;

import com.intellij.docker.dockerFile.parser.psi.DockerFileEnvRegularDeclaration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

import java.util.Collection;
import java.util.HashSet;

class DockerfilePsiElementsVisitor extends PsiRecursiveElementVisitor {
    private final Collection<KeyValuePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof DockerFileEnvRegularDeclaration) {
            this.visitProperty((DockerFileEnvRegularDeclaration) element);
        }

        super.visitElement(element);
    }

    private void visitProperty(DockerFileEnvRegularDeclaration envRegularDeclaration) {
        if (StringUtils.isNotBlank(envRegularDeclaration.getDeclaredName().getText()) && envRegularDeclaration.getRegularValue() != null) {
            collectedItems.add(new KeyValuePsiElement(
                envRegularDeclaration.getDeclaredName().getText(),
                envRegularDeclaration.getRegularValue().getText(),
                envRegularDeclaration));
        }
    }

    Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
