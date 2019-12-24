// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author nik
 */
@ApiStatus.Internal
public class OrderRootsCache {
  private final AtomicReference<ConcurrentMap<CacheKey, VirtualFilePointerContainer>> myRoots = new AtomicReference<>();
  private final Disposable myParentDisposable;
  private Disposable myRootsDisposable; // accessed in EDT

  @ApiStatus.Internal
  public OrderRootsCache(@NotNull Disposable parentDisposable) {
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

  private VirtualFilePointerContainer createContainer(@NotNull Collection<String> urls) {
    // optimization: avoid creating heavy container for empty list, use 'EMPTY' stub for that case
    VirtualFilePointerContainer container;
    if (urls.isEmpty()) {
      container = EMPTY;
    }
    else {
      container = VirtualFilePointerManager.getInstance().createContainer(myRootsDisposable);
      ((VirtualFilePointerContainerImpl)container).addAll(urls);
    }
    return container;
  }

  private VirtualFilePointerContainer getOrComputeContainer(@NotNull OrderRootType rootType,
                                                            int flags,
                                                            @NotNull Supplier<? extends Collection<String>> rootUrlsComputer) {
    ConcurrentMap<CacheKey, VirtualFilePointerContainer> map = myRoots.get();
    CacheKey key = new CacheKey(rootType, flags);
    VirtualFilePointerContainer cached = map == null ? null : map.get(key);
    if (cached == null) {
      map = ConcurrencyUtil.cacheOrGet(myRoots, ContainerUtil.newConcurrentMap());
      cached = map.computeIfAbsent(key, __ -> createContainer(rootUrlsComputer.get()));
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
    return container == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : container.getUrls();
  }

  @ApiStatus.Internal
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
