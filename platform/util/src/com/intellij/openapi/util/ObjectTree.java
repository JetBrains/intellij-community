// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ObjectTree {
  // map of Disposable -> ObjectNode which has myChildren with ObjectNode.myObject == key
  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Map<Disposable, ObjectNode> myObject2ParentNode = new Reference2ObjectOpenHashMap<>(); // guarded by treeLock
  // Disposable -> Throwable (for trace) or UNKNOWN_TRACE (if trace unavailable)
  private final Map<Disposable, Throwable> myDisposedObjects = CollectionFactory.createWeakIdentityMap(100, 0.5f); // guarded by treeLock
  private static final Throwable UNKNOWN_TRACE = new Throwable();
  private final ObjectNode myRootNode = ObjectNode.createRootNode(); // root objects are stored in this node children
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();

  void register(@NotNull Disposable parent, @NotNull Disposable child) throws RuntimeException {
    if (parent == child) {
      throw new IllegalArgumentException("Cannot register to itself: "+parent);
    }
    synchronized (getTreeLock()) {
      if (isDisposed(parent)) {
        throw new IncorrectOperationException("Sorry but parent: " + parent + " (" + parent.getClass()+") has already been disposed " +
                                              "(see the cause for stacktrace) so the child: "+child+ " (" + child.getClass()+") will never be disposed",
                                              getDisposalTrace(parent));
      }

      myDisposedObjects.remove(child);
      if (child instanceof Disposer.CheckedDisposableImpl) {
        // if we dispose a child and then register it back, it means it's not disposed anymore
        ((Disposer.CheckedDisposableImpl)child).isDisposed = false;
      }

      ObjectNode parentNode = getParentNode(parent).findOrCreateChildNode(parent);
      ObjectNode childNode = getParentNode(child).moveChildNodeToOtherParent(child, parentNode);
      myObject2ParentNode.put(child, parentNode);

      assert childNode.getObject() == child;
      checkWasNotAddedAlreadyAsChild(parentNode, childNode);
    }
  }

  private @NotNull ObjectNode getParentNode(@NotNull Disposable object) {
    return ObjectUtils.chooseNotNull(myObject2ParentNode.get(object), myRootNode);
  }

  boolean tryRegister(@NotNull Disposable parent, @NotNull Disposable child) {
    synchronized (getTreeLock()) {
      if (isDisposed(parent)) {
        return false;
      }
      register(parent, child);
      return true;
    }
  }

  Throwable getDisposalTrace(@NotNull Disposable object) {
    synchronized (getTreeLock()) {
      Throwable obj = myDisposedObjects.get(object);
      return obj == UNKNOWN_TRACE ? null : obj;
    }
  }

  boolean isDisposed(@NotNull Disposable object) {
    if (object instanceof CheckedDisposable) {
      return ((CheckedDisposable)object).isDisposed();
    }
    synchronized (getTreeLock()) {
      return myDisposedObjects.get(object) != null;
    }
  }

  private void checkWasNotAddedAlreadyAsChild(@NotNull ObjectNode childNode, @NotNull ObjectNode parentNode) throws IncorrectOperationException {
    for (ObjectNode node = childNode; node != myRootNode && node != null; node = myObject2ParentNode.get(node.getObject())) {
      if (node == parentNode) {
        throw new IncorrectOperationException("'"+childNode.getObject() + "' was already added as a child of '" + parentNode.getObject()+"'");
      }
    }
  }

  private void runWithTrace(@NotNull BiFunction<? super ObjectTree, ? super Throwable, ? extends @NotNull List<Disposable>> removeFromTreeAction) {
    Throwable trace = null;
    boolean needTrace = Disposer.isDebugMode() && (trace = ourTopmostDisposeTrace.get()) == null;
    if (needTrace) {
      trace = ThrowableInterner.intern(new Throwable());
      ourTopmostDisposeTrace.set(trace);
    }

    // first, atomically remove disposables from the tree to avoid "register during dispose" race conditions
    List<Disposable> disposables;
    synchronized (getTreeLock()) {
      disposables = removeFromTreeAction.apply(this, trace);
    }
    // second, call "beforeTreeDispose" in pre-order (some clients are hardcoded to see parents-then-children order in "beforeTreeDispose")
    List<Throwable> exceptions = null;
    for (int i = disposables.size() - 1; i >= 0; i--) {
      Disposable disposable = disposables.get(i);
      if (disposable instanceof Disposable.Parent) {
        try {
          ((Disposable.Parent)disposable).beforeTreeDispose();
        }
        catch (Throwable t) {
          if (exceptions == null) exceptions = new SmartList<>();
          exceptions.add(t);
        }
      }
    }

    // third, dispose in post-order (bottom-up)
    for (Disposable disposable: disposables) {
      try {
        //noinspection SSBasedInspection
        disposable.dispose();
      }
      catch (Throwable e) {
        if (exceptions == null) exceptions = new SmartList<>();
        exceptions.add(e);
      }
    }
    if (needTrace) {
      ourTopmostDisposeTrace.remove();
    }
    if (exceptions != null) {
      handleExceptions(exceptions);
    }
  }

  void executeAllChildren(@NotNull Disposable object, @NotNull Predicate<? super Disposable> predicate) {
    runWithTrace((tree, trace) -> {
      ObjectNode parentNode = getParentNode(object);
      ObjectNode node = parentNode.findChildNode(object);
      if (node == null) {
        return Collections.emptyList();
      }
      List<Disposable> disposables = new ArrayList<>();
      node.removeChildNodesRecursively(disposables, tree, trace, predicate);
      for (Disposable disposable : disposables) {
        myObject2ParentNode.remove(disposable);
      }
      return disposables;
    });
  }

  void executeAll(@NotNull Disposable object, boolean processUnregistered) {
    runWithTrace((tree, trace) -> {
      ObjectNode parentNode = getParentNode(object);
      ObjectNode node = parentNode.findChildNode(object);
      if (node == null && !processUnregistered) {
        return Collections.emptyList();
      }
      List<Disposable> disposables = new ArrayList<>();
      if (node != null) {
        node.removeChildNodesRecursively(disposables, tree, trace, null);
        parentNode.removeChildNode(node);
      }
      if (rememberDisposedTrace(object, trace) == null) {
        disposables.add(object);
      }
      for (Disposable disposable : disposables) {
        myObject2ParentNode.remove(disposable);
      }
      return disposables;
    });
  }

  private Object getTreeLock() {
    return myRootNode;
  }

  private static void handleExceptions(@NotNull List<? extends Throwable> exceptions) {
    if (exceptions.isEmpty()) {
      return;
    }

    for (Throwable exception : exceptions) {
      if (Logger.shouldRethrow(exception)) {
        // wrap with RuntimeException to avoid logger warning about logging ControlFlowException
        // we don't want to rethrow PCEs as well because it may fail a pipeline of disposals
        getLogger().error(new RuntimeException("CE must not be thrown from a dispose() implementation", exception));
      }
      else {
        getLogger().error(exception);
      }
    }
  }

  @TestOnly
  public void assertNoReferenceKeptInTree(@NotNull Disposable disposable) {
    synchronized (getTreeLock()) {
      for (ObjectNode node : myObject2ParentNode.values()) {
        node.assertNoReferencesKept(disposable);
      }
    }
  }

  @TestOnly
  public void assertNoReferenceKeptInTree(@NotNull Class<Disposable> disposableClass) {
    synchronized (getTreeLock()) {
      for (ObjectNode node : myObject2ParentNode.values()) {
        node.assertNoReferencesKept(disposableClass);
      }
    }
  }


  @ApiStatus.Internal
  void assertIsEmpty(boolean throwError) {
    synchronized (getTreeLock()) {
      myRootNode.assertNoChildren(throwError);
    }
  }

  Throwable getRegistrationTrace(@NotNull Disposable object) {
    synchronized (getTreeLock()) {
      ObjectNode objectNode = getParentNode(object).findChildNode(object);
      return objectNode == null ? null : objectNode.getTrace();
    }
  }

  static @NotNull Logger getLogger() {
    return Logger.getInstance(ObjectTree.class);
  }

  // return old value
  Throwable rememberDisposedTrace(@NotNull Disposable object, @Nullable Throwable trace) {
    if (object instanceof CheckedDisposable) {
      return null;
    }
    return myDisposedObjects.put(object, ObjectUtils.notNull(trace, UNKNOWN_TRACE));
  }

  void clearDisposedObjectTraces() {
    synchronized (getTreeLock()) {
      myDisposedObjects.clear();
      for (ObjectNode value : myObject2ParentNode.values()) {
        value.clearTrace();
      }
    }
  }
}
