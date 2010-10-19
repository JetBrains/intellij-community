package org.jetbrains.android.fileTypes;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidFileTypeFactory extends FileTypeFactory {
    public void createFileTypes(@NotNull FileTypeConsumer fileTypeConsumer) {
        fileTypeConsumer.consume(AndroidIdlFileType.ourFileType, AndroidIdlFileType.DEFAULT_ASSOCIATED_EXTENSION);
    }
}
