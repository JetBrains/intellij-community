// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.function.Predicate;

final class ObjectNode {
  private ObjectNode myParent; // guarded by myTree.treeLock
  private final Disposable myObject;
  private List<ObjectNode> myChildren; // guarded by myTree.treeLock
  private Throwable myTrace; // guarded by myTree.treeLock

  ObjectNode(@NotNull Disposable object, @NotNull ObjectNode parentNode) {
    myParent = parentNode;
    myObject = object;

    myTrace = parentNode.isRoot() && Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
  }

  // root
  private ObjectNode() {
    myObject = ROOT_DISPOSABLE;
    myParent = this;
  }
  private static final Disposable ROOT_DISPOSABLE = Disposer.newDisposable();

  void assertNoChildren(boolean throwError) {
    if (myChildren != null) {
      for (ObjectNode childNode : myChildren) {
        if (childNode == null) {
          continue;
        }
        Disposable object = childNode.getObject();
        Throwable trace = childNode.getTrace();
        String message = "Memory leak detected: '" + object + "' of " + object.getClass() + " is registered in Disposer but wasn't disposed.\n" +
                         "Register it with a proper parentDisposable or ensure that it's always disposed by direct Disposer.dispose call.\n" +
                         "See https://jetbrains.org/intellij/sdk/docs/basics/disposers.html for more details.\n" +
                         "The corresponding Disposer.register() stacktrace is shown as the cause:\n";
        RuntimeException exception = new RuntimeException(message, trace);
        if (throwError) {
          throw exception;
        }
        ObjectTree.getLogger().error(exception);
      }
    }
  }

  private boolean isRoot() {
    return myObject == ROOT_DISPOSABLE;
  }

  @NotNull
  static ObjectNode createRoot() {
    return new ObjectNode();
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
  }

  @NotNull
  ObjectNode getParent() {
    return myParent;
  }

  void getAndRemoveRecursively(@NotNull ObjectTree myTree, @NotNull List<? super Disposable> result) {
    getAndRemoveChildrenRecursively(myTree, result, null);
    myTree.removeObjectFromTree(this);
    // already disposed. may happen when someone does `register(obj, ()->Disposer.dispose(t));` abomination
    if (myTree.rememberDisposedTrace(myObject) == null) {
      result.add(myObject);
    }
    myChildren = null;
    myParent = null;
  }

  /**
   * {@code predicate} is used only for direct children.
   */
  void getAndRemoveChildrenRecursively(@NotNull ObjectTree myTree,
                                       @NotNull List<? super Disposable> result, @Nullable Predicate<? super Disposable> predicate) {
    if (myChildren != null) {
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        ObjectNode childNode = myChildren.get(i);
        if (predicate == null || predicate.test(childNode.getObject())) {
          childNode.getAndRemoveRecursively(myTree, result);
        }
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

  void clearTrace() {
    myTrace = null;
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
}
