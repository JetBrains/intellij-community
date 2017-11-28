/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
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
public class VirtualFilePointerTracker {
  private static final Set<VirtualFilePointerImpl> storedPointers = ContainerUtil.newIdentityTroveSet();
  private static Throwable trace;
  private static boolean isTracking; // true when storePointers() was called but before assertPointersDisposed(). false otherwise

  public VirtualFilePointerTracker() {
    storePointers();
  }

  private synchronized void storePointers() {
    if (isTracking) {
      throw new IllegalStateException("Previous test did not call assertPointersAreDisposed() - see 'Caused by:' for its stacktrace", trace);
    }
    trace = new Throwable();
    storedPointers.clear();
    addAllPointersTo(storedPointers);
    //System.out.println("VFPT.storePointers(" + storedPointers + ")");
    isTracking = true;
  }

  public synchronized void assertPointersAreDisposed() {
    if (!isTracking) {
      throw new IllegalStateException("Double call of assertPointersAreDisposed() - see 'Caused by:' for the previous call", trace);
    }
    List<VirtualFilePointerImpl> pointers = new ArrayList<>();
    addAllPointersTo(pointers);
    //System.out.println("VFPT.assertPointersAreDisposed(" +pointers+")");
    for (int i = pointers.size() - 1; i >= 0; i--) {
      VirtualFilePointerImpl pointer = pointers.get(i);
      if (storedPointers.remove(pointer)) {
        pointers.remove(i);
      }
    }
    try {
      Set<VirtualFilePointerImpl> leaked = ContainerUtil.newTroveSet(new TObjectHashingStrategy<VirtualFilePointerImpl>() {
        @Override
        public int computeHashCode(VirtualFilePointerImpl pointer) {
          return FileUtil.PATH_HASHING_STRATEGY.computeHashCode(pointer.getUrl());
        }

        @Override
        public boolean equals(VirtualFilePointerImpl o1, VirtualFilePointerImpl o2) {
          return FileUtil.PATH_HASHING_STRATEGY.equals(o1.getUrl(), o2.getUrl());
        }
      }, pointers);
      leaked.removeAll(storedPointers);

      for (VirtualFilePointerImpl pointer : leaked) {
        pointer.throwDisposalError("Virtual pointer '" + pointer +
                                   "' hasn't been disposed: " + pointer.getStackTrace());
      }
    }
    finally {
      storedPointers.clear();
      trace = new Throwable();
      isTracking = false;
    }
  }

  private static void addAllPointersTo(@NotNull Collection<VirtualFilePointerImpl> pointers) {
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).addAllPointersTo(pointers);
  }
}
