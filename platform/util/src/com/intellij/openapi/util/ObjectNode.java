// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

final class ObjectNode {
  private final Disposable myObject;
  @NotNull
  private List<ObjectNode> myChildren = Collections.emptyList(); // guarded by ObjectTree.treeLock
  private Throwable myTrace; // guarded by ObjectTree.treeLock

  private ObjectNode(@NotNull Disposable object, boolean parentIsRoot) {
    myObject = object;
    myTrace = parentIsRoot && Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
  }

  // root
  private ObjectNode() {
    myObject = ROOT_DISPOSABLE;
  }
  private static final Disposable ROOT_DISPOSABLE = Disposer.newDisposable("ROOT_DISPOSABLE");

  void assertNoChildren(boolean throwError) {
    for (ObjectNode childNode : myChildren) {
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

  private boolean isRootNode() {
    return myObject == ROOT_DISPOSABLE;
  }

  @NotNull
  static ObjectNode createRootNode() {
    return new ObjectNode();
  }

  private void addChildNode(@NotNull ObjectNode childNode) {
    List<ObjectNode> children = myChildren;
    if (children == Collections.<ObjectNode>emptyList()) {
      myChildren = new SmartList<>(childNode);
    }
    else {
      children.add(childNode);
    }
  }

  void removeChildNode(@NotNull ObjectNode childNode) {
    List<ObjectNode> children = myChildren;
    // optimisation: iterate backwards
    for (int i = children.size() - 1; i >= 0; i--) {
      ObjectNode node = children.get(i);
      if (node.equals(childNode)) {
        children.remove(i);
        break;
      }
    }
  }

  @NotNull
  ObjectNode moveChildNodeToOtherParent(@NotNull Disposable child, @NotNull ObjectNode otherParentNode) {
    List<ObjectNode> children = myChildren;
    // optimisation: iterate backwards
    ObjectNode childNode = null;
    for (int i = children.size() - 1; i >= 0; i--) {
      ObjectNode node = children.get(i);
      if (node.getObject() == child) {
        childNode = children.remove(i);
        break;
      }
    }
    if (childNode == null) {
      childNode = new ObjectNode(child, isRootNode());
    }
    otherParentNode.addChildNode(childNode);
    return childNode;
  }

  /**
   * {@code predicate} is used only for direct children
   */
  void removeChildNodesRecursively(@NotNull List<? super Disposable> disposables,
                                   @NotNull ObjectTree tree,
                                   @Nullable Throwable trace,
                                   @Nullable Predicate<? super Disposable> predicate) {
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      ObjectNode childNode = myChildren.get(i);
      Disposable object = childNode.getObject();
      if (predicate == null || predicate.test(object)) {
        myChildren.remove(i);
        // predicate is used only for direct children
        childNode.removeChildNodesRecursively(disposables, tree, trace, null);
        // already disposed. may happen when someone does `register(obj, ()->Disposer.dispose(t));` abomination
        boolean alreadyDisposed = tree.rememberDisposedTrace(object, trace) != null;
        if (!alreadyDisposed) {
          disposables.add(object);
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
    return isRootNode() ? "ROOT" : "Node: " + myObject;
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
    for (ObjectNode node: myChildren) {
      node.assertNoReferencesKept(aDisposable);
    }
  }

  ObjectNode findChildNode(@NotNull Disposable object) {
    List<ObjectNode> children = myChildren;
    for (ObjectNode node : children) {
      if (node.getObject() == object) {
        return node;
      }
    }
    return null;
  }

  @NotNull
  ObjectNode findOrCreateChildNode(@NotNull Disposable object) {
    ObjectNode existing = findChildNode(object);
    if (existing != null) {
      return existing;
    }
    ObjectNode childNode = new ObjectNode(object, isRootNode());
    addChildNode(childNode);
    return childNode;
  }

  // must not override hasCode/equals because ObjectNode must have identity semantics
}
