package ru.adelf.idea.dotenv.php;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;

public class PhpEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(PhpFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if(psiFile instanceof PhpFile) {
            PhpEnvironmentCallsVisitor visitor = new PhpEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
