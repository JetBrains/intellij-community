package ru.adelf.idea.dotenv;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DotEnvFileType extends LanguageFileType {
    public static final DotEnvFileType INSTANCE = new DotEnvFileType();

    private DotEnvFileType() {
        super(DotEnvLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return ".env file";
    }

    @NotNull
    @Override
    public String getDescription() {
        return ".env file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "env";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Text;
    }
}