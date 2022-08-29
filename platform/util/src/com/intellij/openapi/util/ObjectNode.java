// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class ObjectNode {
  @VisibleForTesting
  static final int REASONABLY_BIG = 500;

  private final Disposable myObject;
  @NotNull
  private NodeChildren myChildren = EMPTY; // guarded by ObjectTree.treeLock
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
    for (ObjectNode childNode : myChildren.getAllNodes()) {
      Disposable object = childNode.getObject();
      Throwable trace = childNode.getTrace();
      String message =
        "Memory leak detected: '" + object + "' of " + object.getClass() + " is registered in Disposer but wasn't disposed.\n" +
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
    if (myChildren == EMPTY) {
      myChildren = new ListNodeChildren();
    }
    if (myChildren instanceof ListNodeChildren && myChildren.getSize() >= REASONABLY_BIG) {
      myChildren = ((ListNodeChildren)myChildren).convertToMapImpl();
    }
    myChildren.addChildNode(childNode);
  }

  void removeChildNode(@NotNull ObjectNode childNode) {
    myChildren.removeChildNode(childNode.getObject());
    switchChildrenImplIfNeeded();
  }

  @NotNull
  ObjectNode moveChildNodeToOtherParent(@NotNull Disposable child, @NotNull ObjectNode otherParentNode) {
    ObjectNode childNode = myChildren.removeChildNode(child);
    if (childNode == null) {
      childNode = new ObjectNode(child, isRootNode());
    }
    else {
      switchChildrenImplIfNeeded();
    }
    otherParentNode.addChildNode(childNode);
    assert childNode.getObject() == child;
    return childNode;
  }

  /**
   * {@code predicate} is used only for direct children
   */
  void removeChildNodesRecursively(@NotNull List<? super Disposable> disposables,
                                   @NotNull ObjectTree tree,
                                   @Nullable Throwable trace,
                                   @Nullable Predicate<? super Disposable> predicate) {
    myChildren.removeChildren(predicate, childNode -> {
      // predicate is used only for direct children
      childNode.removeChildNodesRecursively(disposables, tree, trace, null);
      // already disposed. may happen when someone does `register(obj, ()->Disposer.dispose(t));` abomination
      Disposable object = childNode.getObject();
      boolean alreadyDisposed = tree.rememberDisposedTrace(object, trace) != null;
      if (!alreadyDisposed) {
        disposables.add(object);
      }
    });

    switchChildrenImplIfNeeded();
  }

  private void switchChildrenImplIfNeeded() {
    if (myChildren instanceof MapNodeChildren && myChildren.getSize() <= REASONABLY_BIG) {
      myChildren = ((MapNodeChildren)myChildren).convertToListImpl();
    }
    else if (myChildren instanceof ListNodeChildren && myChildren.getSize() == 0) {
      myChildren = EMPTY;
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
    for (ObjectNode node : myChildren.getAllNodes()) {
      node.assertNoReferencesKept(aDisposable);
    }
  }

  ObjectNode findChildNode(@NotNull Disposable object) {
    return myChildren.findChildNode(object);
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

  private static class MapNodeChildren implements NodeChildren {
    private final Map<Disposable, ObjectNode> myChildren = new Reference2ObjectOpenHashMap<>();

    @Override
    public @NotNull Collection<ObjectNode> getAllNodes() {
      return myChildren.values();
    }

    @Override
    public @Nullable ObjectNode removeChildNode(@NotNull Disposable object) {
      return myChildren.remove(object);
    }

    @Override
    public @Nullable ObjectNode findChildNode(@NotNull Disposable object) {
      return myChildren.get(object);
    }

    @Override
    public void addChildNode(@NotNull ObjectNode node) {
      myChildren.put(node.getObject(), node);
    }

    @Override
    public void removeChildren(@Nullable Predicate<? super Disposable> condition, @NotNull Consumer<ObjectNode> deletedNodeConsumer) {
      Iterator<Map.Entry<Disposable, ObjectNode>> iterator = myChildren.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<Disposable, ObjectNode> entry = iterator.next();
        if (condition == null || condition.test(entry.getKey())) {
          ObjectNode value = entry.getValue();
          iterator.remove();
          deletedNodeConsumer.accept(value);
        }
      }
    }

    @Override
    public int getSize() {
      return myChildren.size();
    }

    NodeChildren convertToListImpl() {
      ListNodeChildren children = new ListNodeChildren();
      children.addChildNodes(getAllNodes());
      return children;
    }

    void addChildNodes(@NotNull Collection<ObjectNode> nodes) {
      for (ObjectNode node : nodes) {
        myChildren.put(node.getObject(), node);
      }
    }
  }

  private static class ListNodeChildren implements NodeChildren {
    @NotNull
    private final List<ObjectNode> myChildren;

    private ListNodeChildren() {
      myChildren = new SmartList<>();
    }

    @Override
    public @NotNull Collection<ObjectNode> getAllNodes() {
      return myChildren;
    }

    @Override
    public ObjectNode removeChildNode(@NotNull Disposable nodeToDelete) {
      List<ObjectNode> children = myChildren;
      // optimisation: iterate backwards
      for (int i = children.size() - 1; i >= 0; i--) {
        ObjectNode node = children.get(i);
        if (node.getObject() == nodeToDelete) {
          return children.remove(i);
        }
      }
      return null;
    }

    @Override
    public @Nullable ObjectNode findChildNode(@NotNull Disposable object) {
      for (ObjectNode node : myChildren) {
        if (node.getObject() == object) {
          return node;
        }
      }
      return null;
    }

    @Override
    public void addChildNode(@NotNull ObjectNode node) {
      myChildren.add(node);
    }

    void addChildNodes(@NotNull Collection<ObjectNode> nodes) {
      myChildren.addAll(nodes);
    }

    @Override
    public void removeChildren(@Nullable Predicate<? super Disposable> condition, @NotNull Consumer<ObjectNode> deletedNodeConsumer) {
      for (int i = myChildren.size() - 1; i >= 0; i--) {
        ObjectNode childNode = myChildren.get(i);
        Disposable object = childNode.getObject();
        if (condition == null || condition.test(object)) {
          myChildren.remove(i);
          deletedNodeConsumer.accept(childNode);
        }
      }
    }

    @Override
    public int getSize() {
      return myChildren.size();
    }

    @NotNull NodeChildren convertToMapImpl() {
      MapNodeChildren children = new MapNodeChildren();
      children.addChildNodes(getAllNodes());
      return children;
    }
  }

  private interface NodeChildren {
    @NotNull Collection<ObjectNode> getAllNodes();

    @Nullable ObjectNode removeChildNode(@NotNull Disposable object);

    @Nullable ObjectNode findChildNode(@NotNull Disposable object);

    void addChildNode(@NotNull ObjectNode node);

    void removeChildren(@Nullable Predicate<? super Disposable> condition, @NotNull Consumer<ObjectNode> deletedNodeConsumer);

    int getSize();
  }

  private final static NodeChildren EMPTY = new NodeChildren() {
    @Override
    public @NotNull Collection<ObjectNode> getAllNodes() {
      return Collections.emptyList();
    }

    @Override
    public ObjectNode removeChildNode(@NotNull Disposable object) {
      return null;
    }

    @Override
    public @Nullable ObjectNode findChildNode(@NotNull Disposable object) {
      return null;
    }

    @Override
    public void addChildNode(@NotNull ObjectNode node) {
      throw new IllegalStateException();
    }

    @Override
    public void removeChildren(@Nullable Predicate<? super Disposable> condition, @NotNull Consumer<ObjectNode> deletedNodeConsumer) {
    }

    @Override
    public int getSize() {
      return 0;
    }
  };
}
