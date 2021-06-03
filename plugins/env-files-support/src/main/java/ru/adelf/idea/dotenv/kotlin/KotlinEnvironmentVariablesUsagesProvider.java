package ru.adelf.idea.dotenv.kotlin;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.psi.KtFile;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class KotlinEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(KotlinFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if (psiFile instanceof KtFile) {
            Set<KeyUsagePsiElement> result = new HashSet<>();

            KotlinEnvironmentCallsVisitor visitor = new KotlinEnvironmentCallsVisitor();
            ((KtFile) psiFile).acceptChildren(visitor, result);

            return result;
        }

        return Collections.emptyList();
    }
}
