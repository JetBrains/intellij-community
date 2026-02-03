// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NonNls;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DisposerTest  {
  private MyLoggingDisposable myRoot;
  private MyLoggingDisposable myFolder1;
  private MyLoggingDisposable myFolder2;
  private MyLoggingDisposable myLeaf1;
  private MyLoggingDisposable myLeaf2;
  private final List<MyLoggingDisposable> myDisposedObjects = Collections.synchronizedList(new ArrayList<>());
  @NonNls private final List<String> myDisposeActions = Collections.synchronizedList(new ArrayList<>());

  @Rule
  public TestName name = new TestName();

  @Before
  public void setUp() throws Exception {
    Disposer.setDebugMode(true);
    myRoot = new MyLoggingDisposable("root");

    myFolder1 = new MyLoggingDisposable("folder1");
    myFolder2 = new MyLoggingDisposable("folder2");

    myLeaf1 = new MyLoggingDisposable("leaf1");
    myLeaf2 = new MyLoggingDisposable("leaf2");

    myDisposeActions.clear();
  }

  @After
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myRoot);
    }
    finally {
      myRoot = null;
      myFolder1 = null;
      myFolder2 = null;
      myLeaf1 = null;
      myLeaf2 = null;
      myDisposedObjects.clear();
      myDisposeActions.clear();
    }
  }

  @Test
  public void testDisposalAndAbsenceOfReferences() {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myRoot, myFolder2);
    Disposer.register(myFolder1, myLeaf1);

    Disposer.dispose(myFolder1);
    assertFalse(myRoot.isDisposed());
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertFalse(myFolder2.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);

    Disposer.dispose(myLeaf1);
  }

  @Test
  public void testDisposalOrder() {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myRoot, myFolder2);

    Disposer.dispose(myRoot);

    assertEquals(Arrays.asList(myFolder2, myLeaf1, myFolder1, myRoot), myDisposedObjects);
  }

  @Test
  public void testDisposalOrderNestedDispose() {
    Disposer.register(myRoot, myFolder2);
    //noinspection SSBasedInspection
    Disposer.register(myRoot, () -> Disposer.dispose(myFolder2));

    Disposer.dispose(myRoot);

    assertEquals(Arrays.asList(myFolder2, myRoot), myDisposedObjects);
  }

  @Test
  public void testDirectCallOfDisposable() {
    SelDisposable selfDisposable = new SelDisposable("selfDisposable");
    Disposer.register(myRoot, selfDisposable);
    Disposer.register(selfDisposable, myFolder1);
    Disposer.register(myFolder1, myFolder2);

    //noinspection SSBasedInspection
    selfDisposable.dispose();

    assertDisposed(selfDisposable);
    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
  }

  @Test
  public void testDirectCallOfUnregisteredSelfDisposable() {
    SelDisposable selfDisposable = new SelDisposable("root");
    //noinspection SSBasedInspection
    selfDisposable.dispose();
    assertDisposed(selfDisposable);
  }

  @Test
  public void testRecursiveSelfDisposeCallMustNotReenter() {
    SelDisposable selfDisposable = new SelDisposable("root");
    Disposer.dispose(selfDisposable);
    assertDisposed(selfDisposable);
    assertEquals(1, selfDisposable.disposeCount);
  }

  @Test
  public void testPostponedParentRegistration() {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myLeaf1, myLeaf2);
    Disposer.register(myRoot, myFolder1);


    Disposer.dispose(myRoot);

    assertDisposed(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  @Test
  public void testDisposalOfParentless() {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder1, myFolder2);
    Disposer.register(myFolder2, myLeaf2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  @Test
  public void testDisposalOfParentess2() {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myLeaf2);
    Disposer.register(myFolder1, myFolder2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  @Test
  public void testOverrideParentDisposable() {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myFolder1);
    Disposer.register(myRoot, myFolder1);

    Disposer.dispose(myFolder2);

    assertDisposed(myFolder2);
    assertFalse(myLeaf1.isDisposed());
    assertFalse(myFolder1.isDisposed());

    Disposer.dispose(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
  }

  @Test
  public void testIsDisposingWorksForDisposablesRegisteredWithParent() throws ExecutionException, InterruptedException {
    AtomicBoolean disposeRun = new AtomicBoolean();
    AtomicBoolean allowToContinueDispose = new AtomicBoolean();
    Disposable disposable = () -> {
      disposeRun.set(true);
      while (!allowToContinueDispose.get());
    };
    Disposer.register(myRoot, disposable);

    assertFalse(Disposer.isDisposed(disposable));
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(name.getMethodName()));
    Future<?> future = executor.submit(() -> Disposer.dispose(myRoot));
    while (!disposeRun.get());
    assertTrue(Disposer.isDisposed(disposable));
    assertFalse(future.isDone());
    allowToContinueDispose.set(true);
    future.get();
    assertTrue(Disposer.isDisposed(disposable));
  }

  @Test
  public void testIsDisposingWorksForUnregisteredDisposables() throws ExecutionException, InterruptedException {
    AtomicBoolean disposeRun = new AtomicBoolean();
    AtomicBoolean allowToContinueDispose = new AtomicBoolean();
    Disposable disposable = () -> {
      disposeRun.set(true);
      while (!allowToContinueDispose.get());
    };

    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(name.getMethodName()));
    Future<?> future = executor.submit(() -> Disposer.dispose(disposable));
    while (!disposeRun.get());
    assertTrue(Disposer.isDisposed(disposable));
    assertFalse(future.isDone());
    allowToContinueDispose.set(true);
    future.get();
    assertTrue(Disposer.isDisposed(disposable));
  }

  @Test
  public void testDisposableParentNotify() {
    MyParentDisposable root = new MyParentDisposable("root");
    Disposer.register(root, myFolder1);

    MyParentDisposable sub = new MyParentDisposable("subFolder");
    Disposer.register(myFolder1, sub);

    Disposer.register(sub, myLeaf1);
    Disposer.dispose(root);

    @NonNls String[] expected =
      {"beforeDispose: root", "beforeDispose: subFolder", "dispose: leaf1", "dispose: subFolder", "dispose: folder1", "dispose: root"};

    UsefulTestCase.assertOrderedEquals(myDisposeActions, expected);
  }

  private static void assertDisposed(MyLoggingDisposable disposable) {
    assertTrue(disposable.isDisposed());

    Disposer.getTree().assertNoReferenceKeptInTree(disposable);
  }

  private class MyLoggingDisposable implements Disposable {
    private boolean myDisposed;
    protected String myName;

    private MyLoggingDisposable(@NonNls String aName) {
      myName = aName;
    }

    @Override
    public void dispose() {
      myDisposed = true;
      myDisposedObjects.add(this);
      myDisposeActions.add("dispose: " + myName);
    }

    public boolean isDisposed() {
      return myDisposed;
    }

    @Override
    public String toString() {
      return myName +"; myDisposed="+myDisposed;
    }
  }

  private final class MyParentDisposable extends MyLoggingDisposable implements Disposable.Parent {
    private MyParentDisposable(@NonNls final String aName) {
      super(aName);
    }

    @Override
    public void beforeTreeDispose() {
      myDisposeActions.add("beforeDispose: " + myName);
    }
  }

  private final class SelDisposable extends MyLoggingDisposable {
    int disposeCount;
    private SelDisposable(@NonNls String aName) {
      super(aName);
    }

    @Override
    public void dispose() {
      disposeCount++;
      Disposer.dispose(this);
      super.dispose();
    }
  }

  @Test
  public void testIncest() {
    Disposable parent = Disposer.newDisposable("parent");
    Disposable child = Disposer.newDisposable("child");
    Disposer.register(parent, child);

    Disposable grand = Disposer.newDisposable("grand");
    Disposer.register(child, grand);

    try {
      UsefulTestCase.assertThrows(IncorrectOperationException.class, "'grand' was already added as a child of 'parent'",
                                  () -> Disposer.register(grand, parent));
    }
    finally {
      Disposer.dispose(grand);
      Disposer.dispose(child);
      Disposer.dispose(parent);
    }
  }

  @Test
  public void testRemoveOnlyChildren() {
    Disposable parent = Disposer.newDisposable("parent");
    Disposable child = Disposer.newDisposable("child");
    Disposer.register(parent, child);

    Disposable grand = Disposer.newDisposable("grand");
    Disposer.register(child, grand);

    try {
      Disposer.disposeChildren(parent, __->true);
      assertFalse(Disposer.isDisposed(parent));
      Disposer.dispose(parent);
      assertTrue(Disposer.isDisposed(parent));
    }
    finally {
      Disposer.dispose(grand);
      Disposer.dispose(child);
      Disposer.dispose(parent);
    }
  }

  @Test
  public void testRemoveOnlyChildrenByCondition() {
    Disposable parent = Disposer.newDisposable("parent");
    Disposable child1 = Disposer.newDisposable("child1");
    Disposable child2 = Disposer.newDisposable("child2");
    Disposer.register(parent, child1);
    Disposer.register(parent, child2);

    Disposable grand1 = Disposer.newDisposable("grand1");
    Disposer.register(child1, grand1);

    try {
      Disposer.disposeChildren(parent, disposable -> disposable == child1);
      assertFalse(Disposer.isDisposed(parent));

      assertFalse(Disposer.isDisposed(child2));

      Disposer.dispose(parent);
      assertTrue(Disposer.isDisposed(parent));
    }
    finally {
      Disposer.dispose(grand1);
      Disposer.dispose(child1);
      Disposer.dispose(child2);
      Disposer.dispose(parent);
    }
  }

  @Test
  public void testMustNotRegisterWithAlreadyDisposed() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myRoot, disposable);

    Disposer.dispose(disposable);

    Disposable newDisposable = Disposer.newDisposable();
    UsefulTestCase.assertThrows(IncorrectOperationException.class, () -> Disposer.register(disposable, newDisposable));
  }

  @Test
  public void testMustBeAbleToRegisterThenDisposeThenRegisterAgain() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myRoot, disposable);

    Disposer.dispose(disposable);
    Disposer.register(myRoot, disposable);
    Disposable newDisposable = Disposer.newDisposable();
    Disposer.register(disposable, newDisposable);
    assertFalse(Disposer.isDisposed(disposable));
    assertFalse(Disposer.isDisposed(newDisposable));
  }

  @Test
  public void testDisposeDespiteExceptions() throws Exception {
    DefaultLogger.disableStderrDumping(myRoot);
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      Disposable parent = Disposer.newDisposable();
      Disposable first = Disposer.newDisposable();
      Disposable last = Disposer.newDisposable();

      Disposer.register(parent, first);
      Disposer.register(parent, () -> {
        throw new AssertionError("Expected");
      });

      Disposer.register(parent, last);

      UsefulTestCase.assertThrows(AssertionError.class, "Expected", () -> Disposer.dispose(parent));

      assertTrue(Disposer.isDisposed(parent));
      assertTrue(Disposer.isDisposed(first));
      assertTrue(Disposer.isDisposed(last));
    });
  }

  @Test
  public void testDisposeDespitePCE() throws Exception {
    DefaultLogger.disableStderrDumping(myRoot);
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      Disposable parent = Disposer.newDisposable();
      Disposable first = Disposer.newDisposable();
      Disposable last = Disposer.newDisposable();

      Disposer.register(parent, first);
      Disposer.register(parent, () -> {
        throw new ProcessCanceledException() {
          @Override
          public String getMessage() {
            return "Expected";
          }
        };
      });

      Disposer.register(parent, last);

      UsefulTestCase.assertThrows(RuntimeException.class, "CE must not be thrown from a dispose() implementation",
                                  () -> Disposer.dispose(parent));

      assertTrue(Disposer.isDisposed(parent));
      assertTrue(Disposer.isDisposed(first));
      assertTrue(Disposer.isDisposed(last));
    });
  }

  @Test
  public void testDisposeDespiteCE() throws Exception {
    DefaultLogger.disableStderrDumping(myRoot);
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      Disposable parent = Disposer.newDisposable();
      Disposable first = Disposer.newDisposable();
      Disposable last = Disposer.newDisposable();

      Disposer.register(parent, first);
      Disposer.register(parent, () -> {
        throw new CancellationException("cancelled");
      });

      Disposer.register(parent, last);

      UsefulTestCase.assertThrows(RuntimeException.class, "CE must not be thrown from a dispose() implementation",
                                  () -> Disposer.dispose(parent));

      assertTrue(Disposer.isDisposed(parent));
      assertTrue(Disposer.isDisposed(first));
      assertTrue(Disposer.isDisposed(last));
    });
  }

  @Test
  public void testMustNotAllowToRegisterDuringParentDisposal() throws Exception {
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      DefaultLogger.disableStderrDumping(myRoot);

      Disposable parent = Disposer.newDisposable("parent");
      Disposable last = Disposer.newDisposable("child");

      Disposer.register(parent, () -> Disposer.register(parent, last));

      UsefulTestCase.assertThrows(IncorrectOperationException.class, "Sorry but parent", () -> Disposer.dispose(parent));

      assertTrue(Disposer.isDisposed(parent));
    });
  }

  @Test
  public void testNoLeaksAfterConcurrentDisposeAndRegister() throws Exception {
    long elapsed = TimeoutUtil.measureExecutionTime(() -> {
      AtomicLong leakHash = new AtomicLong();
      try {
        LeakHunter.checkLeak(Disposer.getTree(), MyLoggingDisposable.class, leak -> {
          leakHash.set(System.identityHashCode(leak));
          return true;
        });
      }
      catch (AssertionError e) {
        Assume.assumeNoException("test is ignored because MyLoggingDisposable is already leaking at the test start. " +
                                 "myRoot=" + myRoot + "; ihc(myRoot)=" + System.identityHashCode(myRoot) + "; leakHash=" + leakHash, e);
      }
    });
    Assume.assumeTrue("Too long time (" + elapsed+ "ms) spent hunting memory leak, your heap must be huge! I refuse to continue", elapsed<30_000);
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(name.getMethodName()));

    long deadline = System.currentTimeMillis() + 3 * 60 * 1000;
    for (int i=0; i < 1000 && System.currentTimeMillis() < deadline; i++) {
      myDisposeActions.clear();
      myDisposedObjects.clear();
      AtomicReference<MyLoggingDisposable> parent = new AtomicReference<>(new MyLoggingDisposable("parent" + i));
      AtomicReference<MyLoggingDisposable> child = new AtomicReference<>(new MyLoggingDisposable("child" + i));
      Future<Boolean> future = executor.submit(() -> Disposer.tryRegister(parent.get(), child.get()));

      Disposer.dispose(parent.get());

      boolean registered = future.get();
      assertTrue(parent.get().isDisposed());
      assertEquals(registered, child.get().isDisposed());
      GCWatcher tracking = GCWatcher.tracking(parent.get(), child.get());
      parent.set(null);
      child.set(null);
      myDisposedObjects.clear();
      tracking.ensureCollectedWithinTimeout((int)(System.currentTimeMillis() - deadline));
    }
  }

  @Test
  public void testDisposerMustUseIdentitySemanticsForChildren() {
    List<Disposable> run = new ArrayList<>();
    //noinspection EqualsWhichDoesntCheckParameterClass
    Disposable disposable0 = new Disposable() {
      @Override
      public void dispose() {
        run.add(this);
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public boolean equals(Object obj) {
        return true;
      }
    };
    //noinspection EqualsWhichDoesntCheckParameterClass
    Disposable disposable1 = new Disposable() {
      @Override
      public void dispose() {
        run.add(this);
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public boolean equals(Object obj) {
        return true;
      }
    };
    assertEquals(disposable0, disposable1);

    // for children
    Disposable parent = Disposer.newDisposable();
    Disposer.register(parent, disposable0);
    Disposer.register(parent, disposable1);
    Disposer.dispose(parent);
    assertEquals(2, run.size());
    assertSame(disposable1, run.get(0));
    assertSame(disposable0, run.get(1));
  }

  @Test
  public void testDisposerMustHaveIdentitySemanticsForParent() {
    List<Disposable> run = new ArrayList<>();
    //noinspection EqualsWhichDoesntCheckParameterClass
    Disposable disposable0 = new Disposable() {
      @Override
      public void dispose() {
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public boolean equals(Object obj) {
        return true;
      }
    };
    //noinspection EqualsWhichDoesntCheckParameterClass
    Disposable disposable1 = new Disposable() {
      @Override
      public void dispose() {
      }

      @Override
      public int hashCode() {
        return 0;
      }

      @Override
      public boolean equals(Object obj) {
        return true;
      }
    };
    assertEquals(disposable0, disposable1);

    Disposable child0 = new Disposable() {
      @Override
      public void dispose() {
        run.add(this);
      }
    };
    Disposable child1 = new Disposable() {
      @Override
      public void dispose() {
        run.add(this);
      }
    };
    Disposer.register(disposable0, child0);
    Disposer.register(disposable1, child1);
    Disposer.dispose(disposable0);
    assertSame(child0, UsefulTestCase.assertOneElement(run));
    run.clear();
    Disposer.dispose(disposable1);
    assertSame(child1, UsefulTestCase.assertOneElement(run));
  }

  @Test
  public void testMustNotAllowToRegisterToItself() {
    Disposable d = Disposer.newDisposable();
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> Disposer.register(d, d));
  }

  @Test
  public void testCheckedDisposableMustKnowItsDisposalStatus() {
    CheckedDisposable disposable = Disposer.newCheckedDisposable();
    assertFalse(disposable.isDisposed());
    Disposer.dispose(disposable);
    assertTrue(disposable.isDisposed());

    CheckedDisposable d2 = Disposer.newCheckedDisposable();
    assertTrue(disposable.isDisposed());
    Disposer.register(d2, disposable);
    assertFalse(disposable.isDisposed());
    assertFalse(d2.isDisposed());

    Disposer.dispose(d2);
    assertTrue(d2.isDisposed());
    assertTrue(disposable.isDisposed());
  }

  @Test
  public void testDoubleRegisterMustNotLeakOneOfTheInstances() {
    LeakHunter.checkLeak(Disposer.getTree(), MyLoggingDisposable.class);
    AtomicReference<MyLoggingDisposable> parent = new AtomicReference<>(new MyLoggingDisposable("parent"));
    class MyChildDisposable extends MyLoggingDisposable {
      private MyChildDisposable(String aName) {
        super(aName);
      }
    }
    AtomicReference<MyLoggingDisposable> child = new AtomicReference<>(new MyChildDisposable("child"));
    Disposer.register(parent.get(), child.get());
    Disposer.register(parent.get(), child.get());
    Disposer.dispose(child.get());
    myDisposedObjects.clear();
    GCWatcher tracking = GCWatcher.tracking(child.get());
    child.set(null);
    tracking.ensureCollectedWithinTimeout(60_000);

    Disposer.dispose(parent.get());
    myDisposedObjects.clear();
    GCWatcher trackingPa = GCWatcher.tracking(parent.get());
    parent.set(null);
    trackingPa.ensureCollectedWithinTimeout(60_000);
  }

  @Test
  public void testTryRegisterMustCheckInvariantsToo() {
    Disposable parent = new MyLoggingDisposable("parent");
    Disposable child = new MyLoggingDisposable("child");
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> Disposer.tryRegister(parent, parent));
    assertTrue(Disposer.tryRegister(parent, child));
    UsefulTestCase.assertThrows(IncorrectOperationException.class, () -> Disposer.tryRegister(child, parent));
    Disposer.dispose(parent);
    assertFalse(Disposer.tryRegister(parent, child));
    Disposer.dispose(child);
  }
  
  @Test
  public void testRegisterManyChildren() {
    Disposable parent = new MyLoggingDisposable("parent");
    List<Disposable> children = new ArrayList<>();
    for (int i = 0; i < ObjectNode.REASONABLY_BIG * 2; i++) {
      Disposable child = new MyLoggingDisposable("child #" + i);
      Disposer.register(parent, child);
      children.add(child);
    }
    Disposer.dispose(parent);

    for (Disposable child : children) {
      assertTrue(Disposer.isDisposed(child));
    }
  }

  @PerformanceUnitTest
  @Test
  public void testPerformanceOfRegisterOrDisposeManyChildrenMustBeGood() {
    Disposer.setDebugMode(false); // avoid expensive checks
    int N = 1_000_000;
    Disposable root = Disposer.newDisposable("test_root");

    Disposable[] children = IntStream.range(0, N).mapToObj(i -> Disposer.newDisposable("child " + i)).toArray(Disposable[]::new);
    Benchmark.newBenchmark(name.getMethodName(), () -> {
        for (Disposable child : children) {
          Disposer.register(root, child);
        }
        for (Disposable child : children) {
          Disposer.dispose(child);
        }
      })
      .setup(() -> {
        Disposer.dispose(root);
        Disposer.register(myRoot, root);
      })
      .start();
  }

  @Test
  public void testCheckedDisposableMustNotLeakWhenDisposedAndReRegisteredConcurrently() throws Exception {
    // This test DETERMINISTICALLY detects a race condition where a CheckedDisposable (used as PARENT)
    // can have children registered to it after being removed from the tree but before dispose() is called.
    //
    // The race window (before fix):
    // 1. Thread A: executeAll() removes CheckedDisposable from tree (inside lock)
    // 2. Thread A: releases lock
    // 3. Thread A: calls beforeTreeDispose() on children -- WE BLOCK HERE
    // 4. Thread B: tryRegister(CheckedDisposable, child) sees isDisposed()=false, registers child
    // 5. Thread A: unblocks, calls dispose(), sets isDisposed=true
    // Result: CheckedDisposable is in tree with isDisposed=true -> memory leak
    //
    // The fix: set isDisposed=true inside the lock before releasing it
    // This way tryRegister() sees isDisposed()=true and returns false
    //
    // We use Disposable.Parent.beforeTreeDispose() to create a deterministic race window,
    // since it's called AFTER the lock is released but BEFORE dispose().

    ExecutorService executor = ConcurrencyUtil.newSingleThreadExecutor("testCheckedDisposableMustNotLeakWhenDisposedAndReRegisteredConcurrently");

    try {
      for (int i = 0; i < 10; i++) {
        CountDownLatch inBeforeTreeDispose = new CountDownLatch(1);
        CountDownLatch tryRegisterDone = new CountDownLatch(1);

        // Create a CheckedDisposable to be used as parent
        CheckedDisposable checkedParent = Disposer.newCheckedDisposable("checked-parent-" + i);

        // Register a blocking child that will pause disposal after lock is released
        Disposable.Parent blockingChild = new Disposable.Parent() {
          @Override
          public void beforeTreeDispose() {
            // Signal that we're in the race window (lock released, dispose not called yet)
            inBeforeTreeDispose.countDown();
            try {
              // Wait for the racing thread to complete tryRegister
              tryRegisterDone.await(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          @Override
          public void dispose() {
          }
        };
        Disposer.register(checkedParent, blockingChild);

        // Child to register during the race window
        Disposable racingChild = Disposer.newDisposable("racing-child-" + i);

        // Thread A: dispose the CheckedDisposable parent (will block in beforeTreeDispose)
        Future<?> disposeFuture = executor.submit(() -> Disposer.dispose(checkedParent));

        // Wait for Thread A to enter the race window
        assertTrue("Timed out waiting for beforeTreeDispose",
                   inBeforeTreeDispose.await(10, TimeUnit.SECONDS));

        // Thread B (main thread): try to register during the race window
        // Without fix: succeeds because isDisposed() is still false
        // With fix: fails because isDisposed() was set true inside the lock
        boolean registered = Disposer.tryRegister(checkedParent, racingChild);

        // Let Thread A continue
        tryRegisterDone.countDown();
        disposeFuture.get(10, TimeUnit.SECONDS);

        // Verify: the parent should be disposed
        assertTrue("Parent should be disposed", checkedParent.isDisposed());

        // With the fix: tryRegister should have failed, so parent should NOT be in tree
        // Without fix: tryRegister succeeded, parent is in tree with isDisposed=true (LEAK!)
        if (registered) {
          // If registration succeeded, verify there's no leak
          Disposer.getTree().assertNoReferenceKeptInTree(checkedParent);
        }

        // The key assertion: a disposed CheckedDisposable must not be in the tree
        Disposer.getTree().assertNoReferenceKeptInTree(checkedParent);

        // Cleanup
        Disposer.dispose(racingChild);
      }
    }
    finally {
      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }
}
