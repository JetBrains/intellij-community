package ru.adelf.idea.dotenv;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.FileContent;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.DotEnvFileType;
import ru.adelf.idea.dotenv.api.EnvVariablesProvider;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.util.DotEnvPsiElementsVisitor;

import java.util.Collection;

public class DotEnvVariablesProvider implements EnvVariablesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return file.getFileType().equals(DotEnvFileType.INSTANCE);
    }

    @NotNull
    @Override
    public Collection<Pair<String, String>> getKeyValues(FileContent fileContent) {
        return getVisitorForFile(fileContent).getKeyValues();
    }

    @NotNull
    @Override
    public PsiElement[] getTargetsByKey(String key, FileContent fileContent) {
        return getVisitorForFile(fileContent).getElementsByKey(key);
    }

    private DotEnvPsiElementsVisitor getVisitorForFile(FileContent fileContent) {
        DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();

        PsiFile psiFile = fileContent.getPsiFile();

        if(psiFile instanceof DotEnvFile) {
            psiFile.acceptChildren(visitor);
        }

        return visitor;
    }
}
