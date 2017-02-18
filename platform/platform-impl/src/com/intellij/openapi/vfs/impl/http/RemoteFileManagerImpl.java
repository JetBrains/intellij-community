/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Url;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class RemoteFileManagerImpl extends RemoteFileManager implements Disposable {
  private final LocalFileStorage myStorage;

  private final Map<Url, HttpVirtualFileImpl> remoteFiles = new THashMap<>();
  private final Map<Url, HttpVirtualFileImpl> remoteDirectories = new THashMap<>();

  private final EventDispatcher<HttpVirtualFileListener> myDispatcher = EventDispatcher.create(HttpVirtualFileListener.class);
  private final List<RemoteContentProvider> myProviders = new ArrayList<>();
  private final DefaultRemoteContentProvider myDefaultRemoteContentProvider;

  public RemoteFileManagerImpl() {
    myStorage = new LocalFileStorage();
    myDefaultRemoteContentProvider = new DefaultRemoteContentProvider();
  }

  @NotNull
  public RemoteContentProvider findContentProvider(final @NotNull Url url) {
    for (RemoteContentProvider provider : myProviders) {
      if (provider.canProvideContent(url)) {
        return provider;
      }
    }
    return myDefaultRemoteContentProvider;
  }

  public synchronized HttpVirtualFileImpl getOrCreateFile(@Nullable HttpVirtualFileImpl parent, @NotNull Url url, @NotNull String path, final boolean directory) {
    Map<Url, HttpVirtualFileImpl> cache = directory ? remoteDirectories : remoteFiles;
    HttpVirtualFileImpl file = cache.get(url);
    if (file == null) {
      if (directory) {
        file = new HttpVirtualFileImpl(getHttpFileSystem(url), parent, path, null);
      }
      else {
        RemoteFileInfoImpl fileInfo = new RemoteFileInfoImpl(url, this);
        file = new HttpVirtualFileImpl(getHttpFileSystem(url), parent, path, fileInfo);
        fileInfo.addDownloadingListener(new MyDownloadingListener(file));
      }
      cache.put(url, file);
    }
    return file;
  }

  private static HttpFileSystemBase getHttpFileSystem(@NotNull Url url) {
    return HttpsFileSystem.HTTPS_PROTOCOL.equals(url.getScheme())
           ? HttpsFileSystem.getHttpsInstance() : HttpFileSystemImpl.getInstanceImpl();
  }

  @Override
  public void addRemoteContentProvider(@NotNull final RemoteContentProvider provider, @NotNull Disposable parentDisposable) {
    addRemoteContentProvider(provider);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeRemoteContentProvider(provider);
      }
    });
  }

  @Override
  public void addRemoteContentProvider(@NotNull RemoteContentProvider provider) {
    myProviders.add(provider);
  }

  @Override
  public void removeRemoteContentProvider(@NotNull RemoteContentProvider provider) {
    myProviders.remove(provider);
  }

  @Override
  public void addFileListener(@NotNull final HttpVirtualFileListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeFileListener(@NotNull final HttpVirtualFileListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void fireFileDownloaded(@NotNull VirtualFile file) {
    myDispatcher.getMulticaster().fileDownloaded(file);
  }

  public LocalFileStorage getStorage() {
    return myStorage;
  }

  @Override
  public void dispose() {
    myStorage.deleteDownloadedFiles();
  }

  private class MyDownloadingListener extends FileDownloadingAdapter {
    private final HttpVirtualFileImpl myFile;

    public MyDownloadingListener(final HttpVirtualFileImpl file) {
      myFile = file;
    }

    @Override
    public void fileDownloaded(final VirtualFile localFile) {
      fireFileDownloaded(myFile);
    }
  }
}
