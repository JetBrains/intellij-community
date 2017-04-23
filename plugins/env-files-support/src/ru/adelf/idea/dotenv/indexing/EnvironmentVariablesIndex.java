package ru.adelf.idea.dotenv.indexing;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.DotEnvFileType;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.util.DotEnvPsiElementsVisitor;

import java.util.HashMap;
import java.util.Map;

abstract public class EnvironmentVariablesIndex extends FileBasedIndexExtension<String, Void> {
    private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();

            PsiFile psiFile = fileContent.getPsiFile();

            if(!(psiFile instanceof DotEnvFile)) {
                return map;
            }

            DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();

            psiFile.acceptChildren(visitor);

            for(Pair<String, String> keyValue : visitor.getKeyValues()) {
                map.put(getIndexKey(keyValue), null);
            }

            return map;
        };
    }

    @NotNull
    abstract String getIndexKey(Pair<String, String> keyValue);

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return this.myKeyDescriptor;
    }

    @NotNull
    @Override
    public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> file.getFileType().equals(DotEnvFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 3;
    }
}
