package ru.adelf.idea.dotenv.docker;

import com.intellij.plugins.docker.dockerFile.parser.psi.DockerFileEnvRegularDeclaration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerfilePsiElementsVisitor extends PsiRecursiveElementVisitor {
    final private Set<DockerFileEnvRegularDeclaration> collectedDeclarations = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof DockerFileEnvRegularDeclaration) {
            this.visitProperty((DockerFileEnvRegularDeclaration) element);
        }

        super.visitElement(element);
    }

    private void visitProperty(DockerFileEnvRegularDeclaration envRegularDeclaration) {
        if(StringUtils.isNotBlank(envRegularDeclaration.getDeclaredName().getText())) {
            collectedDeclarations.add(envRegularDeclaration);
        }
    }

    @NotNull
    public Collection<Pair<String, String>> getKeyValues() {
        return this.collectedDeclarations.stream().map(declaration -> {
            return new Pair<>(declaration.getDeclaredName().getText(), declaration.getEnvRegularValue().getText());
        }).collect(Collectors.toList());
    }

    @NotNull
    public Set<PsiElement> getElementsByKey(String key) {
        return this.collectedDeclarations.stream().filter(declaration -> {
            return declaration.getDeclaredName().getText().equals(key);
        }).collect(Collectors.toSet());
    }
}
