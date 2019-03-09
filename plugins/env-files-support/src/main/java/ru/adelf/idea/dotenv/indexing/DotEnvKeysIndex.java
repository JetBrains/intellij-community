package ru.adelf.idea.dotenv.indexing;

import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

public class DotEnvKeysIndex extends EnvironmentVariablesIndex {

    public static final ID<String, Void> KEY = ID.create("ru.adelf.idea.php.dotenv.keys");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    @NotNull
    @Override
    String getIndexKey(KeyValuePsiElement keyValue) {
        return keyValue.getKey().trim();
    }
}
