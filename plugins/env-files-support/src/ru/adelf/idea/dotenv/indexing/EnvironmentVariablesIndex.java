package ru.adelf.idea.dotenv.indexing;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvVariablesProvider;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesProviderUtil;

import java.util.HashMap;
import java.util.Map;

abstract public class EnvironmentVariablesIndex extends FileBasedIndexExtension<String, Void> {
    private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();

            for(EnvVariablesProvider provider : EnvironmentVariablesProviderUtil.PROVIDERS) {
                for(Pair<String, String> keyValue : provider.getKeyValues(fileContent)) {
                    map.put(getIndexKey(keyValue), null);
                }
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
        return file -> {
            for(EnvVariablesProvider provider : EnvironmentVariablesProviderUtil.PROVIDERS) {
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
        return 4;
    }
}
