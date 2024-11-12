package ru.adelf.idea.dotenv.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import ru.adelf.idea.dotenv.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DotEnvFile extends PsiFileBase {
    public DotEnvFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, DotEnvLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return DotEnvFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return ".env file";
    }

    @Override
    public Icon getIcon(int flags) {
        return super.getIcon(flags);
    }
}
