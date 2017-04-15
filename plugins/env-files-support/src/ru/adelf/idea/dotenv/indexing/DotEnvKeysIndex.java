package ru.adelf.idea.dotenv.indexing;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.DotEnvFileType;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.util.DotEnvPsiElementsVisitor;

import java.util.HashMap;
import java.util.Map;

public class DotEnvKeysIndex extends FileBasedIndexExtension<String, Void> {

    public static final ID<String, Void> KEY = ID.create("ru.adelf.idea.php.dotenv.keys");
    private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

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

            for(String key: visitor.getKeys()) {
                map.put(key, null);
            }

            return map;
        };
    }

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
        return 1;
    }
}
