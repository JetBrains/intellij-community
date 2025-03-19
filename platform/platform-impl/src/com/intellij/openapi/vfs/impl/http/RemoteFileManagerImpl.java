// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class RemoteFileManagerImpl extends RemoteFileManager implements Disposable {
  private final LocalFileStorage myStorage;

  private final Map<Url, HttpVirtualFileImpl> remoteFiles = new HashMap<>();
  private final Map<Url, HttpVirtualFileImpl> remoteDirectories = new HashMap<>();

  private final EventDispatcher<HttpVirtualFileListener> myDispatcher = EventDispatcher.create(HttpVirtualFileListener.class);
  private final List<RemoteContentProvider> myProviders = new ArrayList<>();
  private final DefaultRemoteContentProvider myDefaultRemoteContentProvider;

  public RemoteFileManagerImpl() {
    myStorage = new LocalFileStorage();
    myDefaultRemoteContentProvider = new DefaultRemoteContentProvider();
  }

  public @NotNull RemoteContentProvider findContentProvider(final @NotNull Url url) {
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
  public void addRemoteContentProvider(final @NotNull RemoteContentProvider provider, @NotNull Disposable parentDisposable) {
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
  public void addFileListener(final @NotNull HttpVirtualFileListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeFileListener(final @NotNull HttpVirtualFileListener listener) {
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

  private final class MyDownloadingListener extends FileDownloadingAdapter {
    private final HttpVirtualFileImpl myFile;

    MyDownloadingListener(final HttpVirtualFileImpl file) {
      myFile = file;
    }

    @Override
    public void fileDownloaded(final @NotNull VirtualFile localFile) {
      fireFileDownloaded(myFile);
    }
  }
}
