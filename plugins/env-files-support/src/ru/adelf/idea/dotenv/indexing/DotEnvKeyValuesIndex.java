package ru.adelf.idea.dotenv.indexing;

import com.intellij.util.indexing.ID;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;

public class DotEnvKeyValuesIndex extends EnvironmentVariablesIndex {

    public static final ID<String, Void> KEY = ID.create("ru.adelf.idea.php.dotenv.keyValues");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }


    @NotNull
    @Override
    String getIndexKey(Pair<String, String> keyValue) {
        return keyValue.getKey() + "=" + keyValue.getValue();
    }
}
