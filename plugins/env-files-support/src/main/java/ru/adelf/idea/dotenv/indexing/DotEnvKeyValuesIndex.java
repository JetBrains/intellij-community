package ru.adelf.idea.dotenv.indexing;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesProviderUtil;

import java.util.HashMap;
import java.util.Map;

public class DotEnvKeyValuesIndex extends FileBasedIndexExtension<String, String> {

    public static final ID<String, String> KEY = ID.create("ru.adelf.idea.php.dotenv.keyValues");

    @Override
    public @NotNull ID<String, String> getName() {
        return KEY;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, String> map = new HashMap<>();

            boolean storeValues = DotEnvSettings.getInstance().storeValues;

            for (EnvironmentVariablesProvider provider : EnvironmentVariablesProviderUtil.getEnvVariablesProviders()) {
                for (KeyValuePsiElement keyValueElement : provider.getElements(fileContent.getPsiFile())) {
                    if (storeValues) {
                        map.put(keyValueElement.getKey(), keyValueElement.getShortValue());
                    } else {
                        map.put(keyValueElement.getKey(), "");
                    }
                }
            }

            return map;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return file -> {
            for (EnvironmentVariablesProvider provider : EnvironmentVariablesProviderUtil.getEnvVariablesProviders()) {
                if (provider.acceptFile(file).isAccepted()) return true;
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
        return 7;
    }
}
