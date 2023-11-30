// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * Tracks leaks of file pointers from {@link VirtualFilePointerManagerImpl}
 * Usage:
 *
 * <pre>{@code
 * class MyTest {
 *   VirtualFilePointerTracker myTracker;
 *   void setUpOrSomewhereBeforeTestExecution() {
 *     myTracker = new VirtualFilePointerTracker(); // all virtual file pointers created by this moment are remembered
 *   }
 *   void tearDownOrSomewhereAfterTestExecuted() {
 *     myTracker.assertPointersAreDisposed(); // throws if there are virtual file pointers created after setup but never disposed
 *   }
 * }
 * }</pre>
 */
@TestOnly
@Internal
public final class VirtualFilePointerTracker {
  private final Set<VirtualFilePointer> storedPointers = Collections.newSetFromMap(new IdentityHashMap<>());
  private Throwable trace;
  private boolean isTracking; // true when storePointers() was called but before assertPointersDisposed(). false otherwise

  public VirtualFilePointerTracker() {
    storePointers();
  }

  private synchronized void storePointers() {
    if (isTracking) {
      isTracking = false;
      throw new IllegalStateException("Previous test did not call assertPointersAreDisposed() - see 'Caused by:' for its stacktrace", trace);
    }
    trace = new Throwable();
    storedPointers.clear();
    storedPointers.addAll(dumpAllPointers());
    isTracking = true;
  }

  public synchronized void assertPointersAreDisposed() {
    if (!isTracking) {
      throw new IllegalStateException("Double call of assertPointersAreDisposed() - see 'Caused by:' for the previous call", trace);
    }

    List<VirtualFilePointer> pointers = new ArrayList<>(dumpAllPointers());
    for (int i = pointers.size() - 1; i >= 0; i--) {
      VirtualFilePointer pointer = pointers.get(i);
      if (storedPointers.remove(pointer)) {
        pointers.remove(i);
      }
    }

    try {
      Set<VirtualFilePointer> leaked = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
        @Override
        public int hashCode(@Nullable VirtualFilePointer pointer) {
          if (pointer == null) {
            return 0;
          }
          String url = pointer.getUrl();
          return SystemInfoRt.isFileSystemCaseSensitive ? url.hashCode() : Strings.stringHashCodeInsensitive(url);
        }

        @Override
        public boolean equals(VirtualFilePointer o1, VirtualFilePointer o2) {
          return o1 == o2 || (o1 != null && o2 != null &&
                              (SystemInfoRt.isFileSystemCaseSensitive
                               ? o1.getUrl().equals(o2.getUrl())
                               : o1.getUrl().equalsIgnoreCase(o2.getUrl())));
        }
      });
      leaked.addAll(pointers);
      leaked.removeAll(storedPointers);

      for (VirtualFilePointer pointer : leaked) {
        ((VirtualFilePointerImpl)pointer).throwDisposalError("Virtual pointer '" + pointer + "' hasn't been disposed:\n" +
                                                             ((VirtualFilePointerImpl)pointer).getStackTrace());
      }
    }
    finally {
      storedPointers.clear();
      trace = new Throwable();
      isTracking = false;
    }
  }

  private static @NotNull Collection<VirtualFilePointer> dumpAllPointers() {
    return ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).dumpAllPointers();
  }
}
