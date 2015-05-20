package com.jetbrains.javascript.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.Url;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class FileUrlMapper {
  public static final ExtensionPointName<FileUrlMapper> EP_NAME = ExtensionPointName.create("com.jetbrains.fileUrlMapper");

  @NotNull
  public List<Url> getUrls(@NotNull VirtualFile file, @NotNull Project project, @Nullable String currentAuthority) {
    return Collections.emptyList();
  }

  /**
   * Optional to implement, useful if default navigation position to source file is not equals to 0:0 (java file for example)
   */
  @Nullable
  public Navigatable getNavigatable(@NotNull Url url, @NotNull Project project, @Nullable Url requestor) {
    VirtualFile file = getFile(url, project, requestor);
    return file == null ? null : new OpenFileDescriptor(project, file);
  }

  @Nullable
  public abstract VirtualFile getFile(@NotNull Url url, @NotNull Project project, @Nullable Url requestor);

  /**
   * Optional to implement, sometimes you cannot build URL, but can match.
   * Lifetime: resolve session lifetime. Could be called multiple times: n <= total sourcemap count
   */
  @Nullable
  public SourceResolver createSourceResolver(@NotNull VirtualFile file, @NotNull Project project) {
    return null;
  }

  @Nullable
  public FileType getFileType(@NotNull Url url) {
    return null;
  }

  public static abstract class SourceResolver {
    /**
     * Return -1 if no match
     */
    public abstract int resolve(@NotNull ObjectIntHashMap<Url> map, @NotNull Project project);
  }
}