/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
class OrderRootsCache {
  private final AtomicReference<Map<CacheKey, VirtualFilePointerContainer>> myRoots = new AtomicReference<>();
  private final Disposable myParentDisposable;
  private Disposable myRootsDisposable; // accessed in EDT

  OrderRootsCache(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
    disposePointers();
  }

  private void disposePointers() {
    if (myRootsDisposable != null) {
      Disposer.dispose(myRootsDisposable);
    }
    if (!Disposer.isDisposing(myParentDisposable)) {
      Disposer.register(myParentDisposable, myRootsDisposable = Disposer.newDisposable());
    }
  }

  VirtualFilePointerContainer setCachedRoots(@NotNull OrderRootType rootType, int flags, @NotNull Collection<String> urls) {
    final VirtualFilePointerContainer container = VirtualFilePointerManager.getInstance().createContainer(myRootsDisposable);
    for (String url : urls) {
      container.add(url);
    }
    Map<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    if (map == null) map = ConcurrencyUtil.cacheOrGet(myRoots, ContainerUtil.newConcurrentMap());
    map.put(new CacheKey(rootType, flags), container);
    return container;
  }

  @Nullable
  public VirtualFile[] getCachedRoots(@NotNull OrderRootType rootType, int flags) {
    Map<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    final VirtualFilePointerContainer cached = map == null ? null : map.get(new CacheKey(rootType, flags));
    return cached == null ? null : cached.getFiles();
  }

  @Nullable
  public String[] getCachedUrls(@NotNull OrderRootType rootType, int flags) {
    Map<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    final VirtualFilePointerContainer cached = map == null ? null : map.get(new CacheKey(rootType, flags));
    return cached != null ? cached.getUrls() : null;
  }

  public void clearCache() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    disposePointers();
    myRoots.set(null);
  }

  private static final class CacheKey {
    private final OrderRootType myRootType;
    private final int myFlags;

    private CacheKey(@NotNull OrderRootType rootType, int flags) {
      myRootType = rootType;
      myFlags = flags;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CacheKey cacheKey = (CacheKey)o;
      return myFlags == cacheKey.myFlags && myRootType.equals(cacheKey.myRootType);

    }

    @Override
    public int hashCode() {
      return 31 * myRootType.hashCode() + myFlags;
    }
  }
}
