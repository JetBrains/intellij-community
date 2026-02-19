package ru.adelf.idea.dotenv;

import com.intellij.lang.Language;

public class DotEnvLanguage extends Language {
    public static final DotEnvLanguage INSTANCE = new DotEnvLanguage();

    private DotEnvLanguage() {
        super("DotEnv");
    }
}
