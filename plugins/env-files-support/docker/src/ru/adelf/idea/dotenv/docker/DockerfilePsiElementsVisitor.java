package ru.adelf.idea.dotenv.docker;

import com.intellij.docker.dockerFile.parser.psi.DockerFileEnvRegularDeclaration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
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
        var key = envRegularDeclaration.getDeclaredName().getText();
        var valueElement = envRegularDeclaration.getRegularValue();

        if (key != null && !key.isBlank() && valueElement != null) {
            collectedItems.add(new KeyValuePsiElement(key, valueElement.getText(), envRegularDeclaration));
        }
    }

    Collection<KeyValuePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
