// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import org.jetbrains.annotations.NotNull;

public abstract class RemoteFileManager {
  public static RemoteFileManager getInstance() {
    return ApplicationManager.getApplication().getService(RemoteFileManager.class);
  }

  public abstract void addRemoteContentProvider(@NotNull RemoteContentProvider provider, @NotNull Disposable parentDisposable);

  public abstract void addRemoteContentProvider(@NotNull RemoteContentProvider provider);

  public abstract void removeRemoteContentProvider(@NotNull RemoteContentProvider provider);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileListener(@NotNull HttpVirtualFileListener listener);
}
