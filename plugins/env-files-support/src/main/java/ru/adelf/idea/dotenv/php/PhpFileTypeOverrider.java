package ru.adelf.idea.dotenv.php;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class PhpFileTypeOverrider implements FileTypeOverrider {
    @Nullable
    @Override
    public FileType getOverriddenFileType(@NotNull VirtualFile virtualFile) {
        if (virtualFile.getName().startsWith(".env") && virtualFile.getName().endsWith(".php")) {
            return PhpFileType.INSTANCE;
        }

        return null;
    }
}
