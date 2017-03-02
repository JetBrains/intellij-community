package ru.adelf.idea.dotenv.indexing;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharSequenceReader;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

public class DotenvKeysIndex extends FileBasedIndexExtension<String, Void> {

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

            try {
                BufferedReader reader = new BufferedReader(new CharSequenceReader(fileContent.getContentAsText()));
                String line;

                while((line = reader.readLine()) != null) {
                    String[] splitParts = line.split("=");

                    if(splitParts.length < 2) continue;

                    String key = splitParts[0].trim();

                    if(!"".equals(key) && key.charAt(0) != '#') {
                        map.put(key, null);
                    }
                }
            } catch(Exception e) {
                return map;
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
        return ScalarIndexExtension.VOID_DATA_EXTERNALIZER;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() { return file -> ".env".equals(file.getName()) || ".env.example".equals(file.getName()); }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
