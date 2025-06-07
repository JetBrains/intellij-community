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

    @Override
    public @NotNull String getName() {
        return ".env file";
    }

    @Override
    public @NotNull String getDescription() {
        return DotEnvBundle.message("label.env.file");
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "env";
    }

    @Override
    public @Nullable Icon getIcon() {
        return AllIcons.FileTypes.Text;
    }
}