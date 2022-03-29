// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class DisposerTest extends TestCase {
  private MyLoggingDisposable myRoot;
  private MyLoggingDisposable myFolder1;
  private MyLoggingDisposable myFolder2;
  private MyLoggingDisposable myLeaf1;
  private MyLoggingDisposable myLeaf2;
  private final List<MyLoggingDisposable> myDisposedObjects = new ArrayList<>();
  @NonNls private final List<String> myDisposeActions = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Disposer.setDebugMode(true);
    myRoot = new MyLoggingDisposable("root");

    myFolder1 = new MyLoggingDisposable("folder1");
    myFolder2 = new MyLoggingDisposable("folder2");

    myLeaf1 = new MyLoggingDisposable("leaf1");
    myLeaf2 = new MyLoggingDisposable("leaf2");

    myDisposeActions.clear();
  }

  @Override
  protected void tearDown() throws Exception {
    //noinspection SSBasedInspection
    try {
      Disposer.dispose(myRoot);
    }
    finally {
      super.tearDown();
    }
  }

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

  public void testDisposalOrder() {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myRoot, myFolder2);

    Disposer.dispose(myRoot);

    assertEquals(Arrays.asList(myFolder2, myLeaf1, myFolder1, myRoot), myDisposedObjects);
  }

  public void testDisposalOrderNestedDispose() {
    Disposer.register(myRoot, myFolder2);
    //noinspection SSBasedInspection
    Disposer.register(myRoot, () -> Disposer.dispose(myFolder2));

    Disposer.dispose(myRoot);

    assertEquals(Arrays.asList(myFolder2, myRoot), myDisposedObjects);
  }

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

  public void testDirectCallOfUnregisteredSelfDisposable() {
    SelDisposable selfDisposable = new SelDisposable("root");
    //noinspection SSBasedInspection
    selfDisposable.dispose();
  }

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

  public void testIsDisposingWorksForDisposablesRegisteredWithParent() throws ExecutionException, InterruptedException {
    AtomicBoolean disposeRun = new AtomicBoolean();
    AtomicBoolean allowToContinueDispose = new AtomicBoolean();
    Disposable disposable = () -> {
      disposeRun.set(true);
      while (!allowToContinueDispose.get());
    };
    Disposer.register(myRoot, disposable);

    assertFalse(Disposer.isDisposed(disposable));
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(getName()));
    Future<?> future = executor.submit(() -> Disposer.dispose(myRoot));
    while (!disposeRun.get());
    assertTrue(Disposer.isDisposed(disposable));
    assertFalse(future.isDone());
    allowToContinueDispose.set(true);
    future.get();
    assertTrue(Disposer.isDisposed(disposable));
  }

  public void testIsDisposingWorksForUnregisteredDisposables() throws ExecutionException, InterruptedException {
    AtomicBoolean disposeRun = new AtomicBoolean();
    AtomicBoolean allowToContinueDispose = new AtomicBoolean();
    Disposable disposable = () -> {
      disposeRun.set(true);
      while (!allowToContinueDispose.get());
    };

    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(getName()));
    Future<?> future = executor.submit(() -> Disposer.dispose(disposable));
    while (!disposeRun.get());
    assertTrue(Disposer.isDisposed(disposable));
    assertFalse(future.isDone());
    allowToContinueDispose.set(true);
    future.get();
    assertTrue(Disposer.isDisposed(disposable));
  }

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

  private void assertDisposed(MyLoggingDisposable disposable) {
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
      return myName;
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
    private SelDisposable(@NonNls String aName) {
      super(aName);
    }

    @Override
    public void dispose() {
      Disposer.dispose(this);
      super.dispose();
    }
  }

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

  public void testRemoveOnlyChildren() {
    Disposable parent = Disposer.newDisposable("parent");
    Disposable child = Disposer.newDisposable("child");
    Disposer.register(parent, child);

    Disposable grand = Disposer.newDisposable("grand");
    Disposer.register(child, grand);

    try {
      Disposer.disposeChildren(parent, null);
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

  public void testMustNotRegisterWithAlreadyDisposed() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myRoot, disposable);

    Disposer.dispose(disposable);

    Disposable newDisposable = Disposer.newDisposable();
    UsefulTestCase.assertThrows(IncorrectOperationException.class, () -> Disposer.register(disposable, newDisposable));
  }

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

  public void testDisposeDespiteExceptions() {
    DefaultLogger.disableStderrDumping(myRoot);

    Disposable parent = Disposer.newDisposable();
    Disposable first = Disposer.newDisposable();
    Disposable last = Disposer.newDisposable();

    Disposer.register(parent, first);
    Disposer.register(parent, () -> { throw new AssertionError("Expected"); });
    Disposer.register(parent, () -> { throw new ProcessCanceledException() {
      @Override
      public String getMessage() {
        return "Expected";
      }
    }; });
    Disposer.register(parent, last);

    UsefulTestCase.assertThrows(AssertionError.class, "Expected", () -> Disposer.dispose(parent));

    assertTrue(Disposer.isDisposed(parent));
    assertTrue(Disposer.isDisposed(first));
    assertTrue(Disposer.isDisposed(last));
  }

  public void testMustNotAllowToRegisterDuringParentDisposal() {
    DefaultLogger.disableStderrDumping(myRoot);

    Disposable parent = Disposer.newDisposable("parent");
    Disposable last = Disposer.newDisposable("child");

    Disposer.register(parent, () -> Disposer.register(parent, last));

    UsefulTestCase.assertThrows(IncorrectOperationException.class, "Sorry but parent", () -> Disposer.dispose(parent));

    assertTrue(Disposer.isDisposed(parent));
  }

  public void testNoLeaksAfterConcurrentDisposeAndRegister() throws Exception {
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(getName()));

    for (int i = 0; i < 100; i++) {
      MyLoggingDisposable parent = new MyLoggingDisposable("parent");
      Future<?> future = executor.submit(() -> Disposer.tryRegister(parent, new MyLoggingDisposable("child")));

      Disposer.dispose(parent);

      future.get();

      LeakHunter.checkLeak(Disposer.getTree(), MyLoggingDisposable.class);
    }
  }

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

  public void testMustNotAllowToRegisterToItself() {
    Disposable d = Disposer.newDisposable();
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> Disposer.register(d, d));
  }

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

  public void testDoubleRegisterMustNotLeakOneOfTheInstances() {
    LeakHunter.checkLeak(Disposer.getTree(), MyLoggingDisposable.class);
    Disposable parent = new MyLoggingDisposable("parent");
    class MyChildDisposable extends MyLoggingDisposable {
      private MyChildDisposable(String aName) {
        super(aName);
      }
    }
    Disposable child = new MyChildDisposable("child");
    Disposer.register(parent, child);
    Disposer.register(parent, child);
    Disposer.dispose(child);
    //noinspection UnusedAssignment
    child = null;
    myDisposedObjects.clear();
    LeakHunter.checkLeak(Disposer.getTree(), MyChildDisposable.class);
    Disposer.dispose(parent);
    LeakHunter.checkLeak(Disposer.getTree(), MyLoggingDisposable.class);
  }
}
