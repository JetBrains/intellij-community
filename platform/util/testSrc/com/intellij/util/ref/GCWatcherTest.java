// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ref;

import junit.framework.TestCase;
import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public class GCWatcherTest {

  public volatile Object o;
  public volatile Reference<Object> ref;

  @Test
  public void testWeakReferenceCollected() {
    o = new Object();
    ref = new WeakReference<>(o);
    GCWatcher watcher = GCWatcher.tracking(o);
    o = null;
    watcher.setGenerateHeapDump(false);
    watcher.ensureCollected();
  }


  @Test
  public void testWeakReferenceNotCollected() {
    o = new Object();
    ref = new WeakReference<>(o);
    GCWatcher watcher = GCWatcher.tracking(o);
    try {
      watcher.setGenerateHeapDump(false);
      watcher.ensureCollected();
      TestCase.assertNotNull("Wrong test setup: ref should not be GC-ed yet", ref.get());
      TestCase.fail("Should throw IllegalStateException");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  @Test
  public void testSoftReferenceCollected() {
    o = new Object();
    ref = new SoftReference<>(o);
    GCWatcher watcher = GCWatcher.tracking(o);
    o = null;
    watcher.setGenerateHeapDump(false);
    watcher.ensureCollected();
  }

  @Test
  public void testSoftReferenceNotCollected() {
    o = new Object();
    ref = new SoftReference<>(o);
    GCWatcher watcher = GCWatcher.tracking(o);
    try {
      watcher.setGenerateHeapDump(false);
      watcher.ensureCollected();
      TestCase.assertNotNull("Wrong test setup: ref should not be GC-ed yet", ref.get());
      TestCase.fail("Should throw IllegalStateException");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  @Test
  public void testJBSoftReferenceCollected() {
    o = new Object();
    ref = new com.intellij.reference.SoftReference<>(o);
    GCWatcher watcher = GCWatcher.tracking(o);
    o = null;
    watcher.setGenerateHeapDump(false);
    watcher.ensureCollected();
  }
}
