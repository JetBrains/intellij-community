package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Remote("com.intellij.openapi.vfs.VirtualFile")
public interface VirtualFile {
  @NotNull String getName();

  @Nullable VirtualFile findChild(@NotNull String name);

  @Nullable VirtualFile findFileByRelativePath(@NotNull String relPath);
}
