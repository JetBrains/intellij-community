package com.intellij.openapi.vfs.impl.http;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
*/
public interface FileDownloadingListener {

  void fileDownloaded(final VirtualFile localFile);

  void errorOccured(@NotNull String errorMessage);

  void progressMessageChanged(final boolean indeterminate, @NotNull String message);

  void progressFractionChanged(double fraction);
}
