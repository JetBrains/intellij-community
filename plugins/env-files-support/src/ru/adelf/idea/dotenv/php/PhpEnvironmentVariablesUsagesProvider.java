package ru.adelf.idea.dotenv.php;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class PhpEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(PhpFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<String> getKeys(FileContent fileContent) {

        PsiFile psiFile = fileContent.getPsiFile();

        if(psiFile instanceof PhpFile) {
            PhpEnvironmentCallsVisitor visitor = new PhpEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getKeys();
        }

        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Set<PsiElement> getTargetsByKey(String key, PsiFile psiFile) {

        if(psiFile instanceof PhpFile) {
            PhpEnvironmentCallsVisitor visitor = new PhpEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getTargets(key);
        }

        return Collections.emptySet();
    }
}
