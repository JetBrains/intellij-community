package ru.adelf.idea.dotenv;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.util.DotEnvPsiElementsVisitor;

import java.util.Collection;
import java.util.Set;

public class DotEnvVariablesProvider implements EnvironmentVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(DotEnvFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<Pair<String, String>> getKeyValues(FileContent fileContent) {
        return getVisitorForFile(fileContent.getPsiFile()).getKeyValues();
    }

    @NotNull
    @Override
    public Set<PsiElement> getTargetsByKey(String key, PsiFile psiFile) {
        return getVisitorForFile(psiFile).getElementsByKey(key);
    }

    private DotEnvPsiElementsVisitor getVisitorForFile(PsiFile psiFile) {
        DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();

        if(psiFile instanceof DotEnvFile) {
            psiFile.acceptChildren(visitor);
        }

        return visitor;
    }
}
