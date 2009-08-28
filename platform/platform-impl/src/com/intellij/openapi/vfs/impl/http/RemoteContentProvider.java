package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public abstract class RemoteContentProvider {

  public abstract boolean canProvideContent(@NotNull String url);

  public abstract void saveContent(final String url, @NotNull File targetFile, @NotNull DownloadingCallback callback);

  public abstract boolean isUpToDate(@NotNull String url, @NotNull VirtualFile local);


  public interface DownloadingCallback {
    void finished(@Nullable FileType fileType);

    void errorOccured(@NotNull String errorMessage);

    void setProgressText(@NotNull String text, boolean indeterminate);

    void setProgressFraction(double fraction);

    boolean isCancelled();
  }
}
