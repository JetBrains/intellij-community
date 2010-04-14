/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class RemoteFileManager {
  public static RemoteFileManager getInstance() {
    return ServiceManager.getService(RemoteFileManager.class);
  }

  public abstract void addRemoteContentProvider(@NotNull RemoteContentProvider provider, @NotNull Disposable parentDisposable);

  public abstract void addRemoteContentProvider(@NotNull RemoteContentProvider provider);

  public abstract void removeRemoteContentProvider(@NotNull RemoteContentProvider provider);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileListener(@NotNull HttpVirtualFileListener listener);
}
