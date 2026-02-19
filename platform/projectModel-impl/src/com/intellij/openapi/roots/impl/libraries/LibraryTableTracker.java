// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.workspaceModel.ide.legacyBridge.LibraryBridgesKt.findLibraryId;


/**
 * Tracks leaks of libraries from global {@link LibraryTable}
 * Usage:
 *
 * <pre>{@code
 * class MyTest {
 *   LibraryTableTracker myTracker;
 *   void setUpOrSomewhereBeforeTestExecution() {
 *     myTracker = new LibraryTableTracker(); // all libraries configured in global table by this moment are remembered
 *   }
 *   void tearDownOrSomewhereAfterTestExecuted() {
 *     myTracker.assertDisposed(); // throws if there are libraries created after setup but never disposed
 *   }
 * }
 * }</pre>
 */
@TestOnly
public final class LibraryTableTracker {
  private Map<LibraryId, Library> stored;
  private Throwable trace;
  private boolean isTracking; // true when storePointers() was called but before assertPointersDisposed(). false otherwise
  private LibraryTable myLibraryTable;

  public LibraryTableTracker() {
    store();
  }

  private synchronized void store() {
    if (isTracking) {
      isTracking = false;
      if (trace != null) {
        trace.printStackTrace(System.err);
      }
      throw new IllegalStateException("Previous test did not call assertDisposed() - see 'Caused by:' for its stacktrace", trace);
    }
    trace = new Throwable();
    myLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    stored = dumpAll();
    isTracking = true;
  }

  public synchronized void assertDisposed() {
    if (!isTracking) {
      throw new IllegalStateException("Double call of assertDisposed() - see 'Caused by:' for the previous call", trace);
    }

    Map<LibraryId, Library> leaked = dumpAll();
    try {
      stored.keySet().forEach(leaked::remove);
      for (Library library : leaked.values()) {
        System.err.println("Leaked library: "+library+" creation trace:\n"+((TraceableDisposable)library).getStackTrace());
        ((TraceableDisposable)library).throwDisposalError("Leaked library: "+library);
      }
    }
    finally {
      stored = null;
      trace = new Throwable();
      isTracking = false;
      myLibraryTable = null;
    }
  }

  private @NotNull Map<LibraryId, Library> dumpAll() {
    Map<LibraryId, Library> libraries = new HashMap<>();
    for (Library library : myLibraryTable.getLibraries()) {
      libraries.put(findLibraryId(library), library);
    }
    return libraries;
  }
}
