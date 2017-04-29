package ru.adelf.idea.dotenv.indexing;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.PhpFileType;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.php.PhpEnvironmentCallsVisitor;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesProviderUtil;

import java.util.HashMap;
import java.util.Map;

public class DotEnvUsagesIndex extends FileBasedIndexExtension<String, Void> {

    public static final ID<String, Void> KEY = ID.create("ru.adelf.idea.php.dotenv.usages");
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

            for(EnvironmentVariablesUsagesProvider provider : EnvironmentVariablesProviderUtil.USAGES_PROVIDERS) {
                for(String key : provider.getKeys(fileContent)) {
                    map.put(key, null);
                }
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
        return file -> {
            for(EnvironmentVariablesUsagesProvider provider : EnvironmentVariablesProviderUtil.USAGES_PROVIDERS) {
                if(provider.acceptFile(file)) return true;
            }

            return false;
        };
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
