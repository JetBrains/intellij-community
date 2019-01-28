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
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

  private static final VirtualFilePointerContainer EMPTY = ObjectUtils.sentinel("Empty roots container", VirtualFilePointerContainer.class);
  private VirtualFilePointerContainer setCachedRoots(@NotNull CacheKey key, @NotNull Collection<String> urls) {
    // optimization: avoid creating heavy container for empty list, use 'EMPTY' stub for that case
    VirtualFilePointerContainer container;
    if (urls.isEmpty()) {
      container = EMPTY;
    }
    else {
      container = VirtualFilePointerManager.getInstance().createContainer(myRootsDisposable);
      ((VirtualFilePointerContainerImpl)container).addAll(urls);
    }
    Map<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    if (map == null) map = ConcurrencyUtil.cacheOrGet(myRoots, ContainerUtil.newConcurrentMap());
    map.put(key, container);
    return container;
  }

  private VirtualFilePointerContainer getOrComputeContainer(@NotNull OrderRootType rootType,
                                                            int flags,
                                                            @NotNull Supplier<? extends Collection<String>> computer) {
    Map<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    CacheKey key = new CacheKey(rootType, flags);
    VirtualFilePointerContainer cached = map == null ? null : map.get(key);
    if (cached == null) {
      Collection<String> roots = computer.get();
      cached = setCachedRoots(key, roots);
    }
    return cached == EMPTY ? null : cached;
  }

  @NotNull
  VirtualFile[] getOrComputeRoots(@NotNull OrderRootType rootType, int flags, @NotNull Supplier<? extends Collection<String>> computer) {
    VirtualFilePointerContainer container = getOrComputeContainer(rootType, flags, computer);
    return container == null ? VirtualFile.EMPTY_ARRAY : container.getFiles();
  }

  @NotNull
  String[] getOrComputeUrls(@NotNull OrderRootType rootType, int flags, @NotNull Supplier<? extends Collection<String>> computer) {
    VirtualFilePointerContainer container = getOrComputeContainer(rootType, flags, computer);
    return container == null ? ArrayUtil.EMPTY_STRING_ARRAY : container.getUrls();
  }

  void clearCache() {
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
