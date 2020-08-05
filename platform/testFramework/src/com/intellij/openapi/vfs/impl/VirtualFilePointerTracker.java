// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
public final class VirtualFilePointerTracker {
  private static final Set<VirtualFilePointer> storedPointers = new ReferenceOpenHashSet<>();
  private static Throwable trace;
  private static boolean isTracking; // true when storePointers() was called but before assertPointersDisposed(). false otherwise

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
    //System.out.println("VFPT.storePointers(" + storedPointers + ")");
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
      Set<VirtualFilePointer> leaked = new ObjectOpenCustomHashSet<>(pointers, new Hash.Strategy<VirtualFilePointer>() {
        @Override
        public int hashCode(@Nullable VirtualFilePointer pointer) {
          return pointer == null ? 0 : FileUtil.PATH_HASHING_STRATEGY.computeHashCode(pointer.getUrl());
        }

        @Override
        public boolean equals(VirtualFilePointer o1, VirtualFilePointer o2) {
          return o1 == o2 || (o1 != null && o2 != null && FileUtil.PATH_HASHING_STRATEGY.equals(o1.getUrl(), o2.getUrl()));
        }
      });
      leaked.removeAll(storedPointers);

      for (VirtualFilePointer pointer : leaked) {
        ((VirtualFilePointerImpl)pointer).throwDisposalError("Virtual pointer '" + pointer +
                                                                 "' hasn't been disposed: " + ((VirtualFilePointerImpl)pointer).getStackTrace());
      }
    }
    finally {
      storedPointers.clear();
      trace = new Throwable();
      isTracking = false;
    }
  }

  @NotNull
  private static Collection<VirtualFilePointer> dumpAllPointers() {
    return ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).dumpAllPointers();
  }
}
