package ru.adelf.idea.dotenv.python;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;

public class PythonEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(PythonFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if(psiFile instanceof PyFile) {
            PythonEnvironmentCallsVisitor visitor = new PythonEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
