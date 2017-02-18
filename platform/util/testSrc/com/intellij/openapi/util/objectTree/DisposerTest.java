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
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.IncorrectOperationException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.Disposer.newDisposable;

public class DisposerTest extends TestCase {
  private MyDisposable myRoot;

  private MyDisposable myFolder1;
  private MyDisposable myFolder2;

  private MyDisposable myLeaf1;
  private MyDisposable myLeaf2;

  private final List<MyDisposable> myDisposedObjects = new ArrayList<>();

  @NonNls private final List<String> myDisposeActions = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    //if(!Disposer.getTree().isEmpty()) {
    //  Disposer.assertIsEmpty();
    //  fail("Clean leftovers from previous tests");
    //  Disposer.getTree().clearAll();
    //}
    myRoot = new MyDisposable("root");

    myFolder1 = new MyDisposable("folder1");
    myFolder2 = new MyDisposable("folder2");

    myLeaf1 = new MyDisposable("leaf1");
    myLeaf2 = new MyDisposable("leaf2");

    myDisposeActions.clear();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myRoot);
    //assertTrue(Disposer.getTree().isEmpty());
    super.tearDown();
  }

  public void testDisposalAndAbsenceOfReferences() throws Exception {
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

  public void testDisposalOrder() throws Exception {
    Disposer.register(myRoot, myFolder1);
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myRoot, myFolder2);

    Disposer.dispose(myRoot);

    List<MyDisposable> expected = new ArrayList<>();
    expected.add(myFolder2);
    expected.add(myLeaf1);
    expected.add(myFolder1);
    expected.add(myRoot);

    assertEquals(expected, myDisposedObjects);
  }

  public void testDirectCallOfDisposable() throws Exception {
    SelDisposable selfDisposable = new SelDisposable("root");
    Disposer.register(myRoot, selfDisposable);
    Disposer.register(selfDisposable, myFolder1);
    Disposer.register(myFolder1, myFolder2);

    selfDisposable.dispose();

    assertDisposed(selfDisposable);
    assertDisposed(myFolder1);
    assertDisposed(myFolder2);

    assertEquals(0, Disposer.getTree().getNodesInExecution().size());
  }

  public void testDirectCallOfUnregisteredSelfDisposable() throws Exception {
    SelDisposable selfDisposable = new SelDisposable("root");
    selfDisposable.dispose();
  }

  public void testDisposeAndReplace() throws Exception {
    Disposer.register(myRoot, myFolder1);

    Disposer.disposeChildAndReplace(myFolder1, myFolder2);
    assertDisposed(myFolder1);

    Disposer.dispose(myRoot);
    assertDisposed(myRoot);
    assertDisposed(myFolder2);
  }

  public void testPostponedParentRegistration() throws Exception {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myLeaf1, myLeaf2);
    Disposer.register(myRoot, myFolder1);


    Disposer.dispose(myRoot);

    assertDisposed(myRoot);
    assertDisposed(myFolder1);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentless() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder1, myFolder2);
    Disposer.register(myFolder2, myLeaf2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testDisposalOfParentess2() throws Throwable {
    Disposer.register(myFolder1, myLeaf1);
    Disposer.register(myFolder2, myLeaf2);
    Disposer.register(myFolder1, myFolder2);

    Disposer.dispose(myFolder1);

    assertDisposed(myFolder1);
    assertDisposed(myFolder2);
    assertDisposed(myLeaf1);
    assertDisposed(myLeaf2);
  }

  public void testOverrideParentDisposable() throws Exception {
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

  public void testDisposableParentNotify() throws Exception {
    MyParentDisposable root = new MyParentDisposable("root");
    Disposer.register(root, myFolder1);

    MyParentDisposable sub = new MyParentDisposable("subFolder");
    Disposer.register(myFolder1, sub);

    Disposer.register(sub, myLeaf1);

    Disposer.dispose(root);


    @NonNls ArrayList<String> expected = new ArrayList<>();
    expected.add("beforeDispose: root");
    expected.add("beforeDispose: subFolder");
    expected.add("dispose: leaf1");
    expected.add("dispose: subFolder");
    expected.add("dispose: folder1");
    expected.add("dispose: root");


    assertEquals(toString(expected), toString(myDisposeActions));
  }


  private void assertDisposed(MyDisposable disposable) {
    assertTrue(disposable.isDisposed());
    assertFalse(disposable.toString(), Disposer.getTree().containsKey(disposable));

    Disposer.getTree().assertNoReferenceKeptInTree(disposable);
  }

  private class MyDisposable implements Disposable {
    private boolean myDisposed;
    protected String myName;

    private MyDisposable(@NonNls String aName) {
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

    public String toString() {
      return myName;
    }
  }

  private class MyParentDisposable extends MyDisposable implements Disposable.Parent {
    private MyParentDisposable(@NonNls final String aName) {
      super(aName);
    }

    @Override
    public void beforeTreeDispose() {
      myDisposeActions.add("beforeDispose: " + myName);
    }
  }

  private class SelDisposable extends MyDisposable {
    private SelDisposable(@NonNls String aName) {
      super(aName);
    }

    @Override
    public void dispose() {
      Disposer.dispose(this);
      super.dispose();
    }
  }

  private static String toString(List<String> list) {
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < list.size(); i++) {
      String each = list.get(i);
      result.append(each);
      if (i < list.size() - 1) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  public void testIncest() {
    Disposable parent = newDisposable("parent");
    Disposable child = newDisposable("child");
    Disposer.register(parent, child);

    Disposable grand = newDisposable("grand");
    Disposer.register(child, grand);

    try {
      Disposer.register(grand, parent);
      fail("must not allow");
    }
    catch (IncorrectOperationException e) {
      assertEquals("'grand' was already added as a child of 'parent'", e.getMessage());
    }
    finally {
      Disposer.dispose(grand);
      Disposer.dispose(child);
      Disposer.dispose(parent);
    }
  }


  public void testMustNotRegisterWithAlreadyDisposed() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myRoot, disposable);

    Disposer.dispose(disposable);

    try {
      Disposer.register(disposable, Disposer.newDisposable());
      fail("Must not be able to register with already disposed parent");
    }
    catch (IncorrectOperationException ignored) {

    }
  }

  public void testRegisterThenDisposeThenRegisterAgain() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myRoot, disposable);

    Disposer.dispose(disposable);
    Disposer.register(myRoot, disposable);
    Disposer.register(disposable, Disposer.newDisposable());
  }
}
