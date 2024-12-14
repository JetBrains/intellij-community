package ru.adelf.idea.dotenv.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;

public class JavaEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(JavaFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if (psiFile instanceof PsiJavaFile) {
            JavaEnvironmentCallsVisitor visitor = new JavaEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
