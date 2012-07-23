package org.jetbrains.android.resourceManagers;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface FileResourceProcessor {
  boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType);
}
