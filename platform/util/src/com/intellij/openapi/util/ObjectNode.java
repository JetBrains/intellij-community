// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

final class ObjectNode {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance(ObjectNode.class);

  private final ObjectTree myTree;

  private ObjectNode myParent; // guarded by myTree.treeLock
  private final Disposable myObject;

  private List<ObjectNode> myChildren; // guarded by myTree.treeLock
  private final Throwable myTrace;

  ObjectNode(@NotNull ObjectTree tree,
             @Nullable ObjectNode parentNode,
             @NotNull Disposable object) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = parentNode == null && Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
  }

  void addChild(@NotNull ObjectNode child) {
    List<ObjectNode> children = myChildren;
    if (children == null) {
      myChildren = new SmartList<>(child);
    }
    else {
      children.add(child);
    }
    child.myParent = this;
  }

  void removeChild(@NotNull ObjectNode child) {
    List<ObjectNode> children = myChildren;
    if (children != null) {
      // optimisation: iterate backwards
      for (int i = children.size() - 1; i >= 0; i--) {
        ObjectNode node = children.get(i);
        if (node.equals(child)) {
          children.remove(i);
          break;
        }
      }
    }
    child.myParent = null;
  }

  ObjectNode getParent() {
    synchronized (myTree.treeLock) {
      return myParent;
    }
  }

  void execute(@NotNull List<? super Throwable> exceptions) {
    ObjectTree.executeActionWithRecursiveGuard(this, myTree.getNodesInExecution(), each -> {
      if (myTree.getDisposalInfo(myObject) != null) return; // already disposed. may happen when someone does `register(obj, ()->Disposer.dispose(t));` abomination
      try {
        if (myObject instanceof Disposable.Parent) {
          ((Disposable.Parent)myObject).beforeTreeDispose();
        }
      }
      catch (Throwable t) {
        LOG.error(t);
      }

      ObjectNode[] childrenArray;
      synchronized (myTree.treeLock) {
        List<ObjectNode> children = myChildren;
        childrenArray = children == null || children.isEmpty() ? EMPTY_ARRAY : children.toArray(EMPTY_ARRAY);
        myChildren = null;
      }

      for (int i = childrenArray.length - 1; i >= 0; i--) {
        try {
          ObjectNode childNode = childrenArray[i];
          childNode.execute(exceptions);
          synchronized (myTree.treeLock) {
            childNode.myParent = null;
          }
        }
        catch (Throwable e) {
          exceptions.add(e);
        }
      }

      try {
        //noinspection SSBasedInspection
        myObject.dispose();
        myTree.rememberDisposedTrace(myObject);
      }
      catch (Throwable e) {
        exceptions.add(e);
      }
      removeFromObjectTree();
    });
  }

  private void removeFromObjectTree() {
    synchronized (myTree.treeLock) {
      myTree.putNode(myObject, null);
      if (myParent == null) {
        myTree.removeRootObject(myObject);
      }
    }
  }

  @NotNull
  Disposable getObject() {
    return myObject;
  }

  @Override
  @NonNls
  public String toString() {
    return "Node: " + myObject;
  }

  Throwable getTrace() {
    return myTrace;
  }

  @TestOnly
  void assertNoReferencesKept(@NotNull Disposable aDisposable) {
    assert getObject() != aDisposable;
    if (myChildren != null) {
      for (ObjectNode node: myChildren) {
        node.assertNoReferencesKept(aDisposable);
      }
    }
  }

  <D extends Disposable> D findChildEqualTo(@NotNull D object) {
    List<ObjectNode> children = myChildren;
    if (children != null) {
      for (ObjectNode node : children) {
        Disposable nodeObject = node.getObject();
        if (nodeObject.equals(object)) {
          //noinspection unchecked
          return (D)nodeObject;
        }
      }
    }
    return null;
  }
}
