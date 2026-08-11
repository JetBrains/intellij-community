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
    if (!tryRegister(parent, child)) {
      throw new IncorrectOperationException(
        "Sorry but parent: " + parent + " (" + parent.getClass() + ") has already been disposed " +
        "(see the cause for stacktrace) so the child: " + child + " (" + child.getClass() + ") will never be disposed",
        getDisposalTrace(parent));
    }
  }

  private @NotNull ObjectNode getParentNode(@NotNull Disposable object) {
    return ObjectUtils.chooseNotNull(myObject2ParentNode.get(object), myRootNode);
  }

  boolean tryRegister(@NotNull Disposable parent, @NotNull Disposable child) {
    List<Disposable> toDispose = null;
    boolean success = false;
    synchronized (getTreeLock()) {
      if (isDisposed(parent)) {
        // must be called inside the tree lock
        toDispose = collectStrandedChild(child);
      }
      else if (parent == child) {
        throw new IllegalArgumentException("Cannot register to itself: " + parent);
      }
      else {
        myDisposedObjects.remove(child);
        if (child instanceof Disposer.CheckedDisposableImpl) {
          // if we dispose a child and then register it back, it means it's not disposed anymore
          ((Disposer.CheckedDisposableImpl)child).isDisposed = false;
        }

        // main parent -> child wire block
        ObjectNode grandparentNode = getParentNode(parent);
        boolean parentNodeWasNew = grandparentNode.findChildNode(parent) == null;
        ObjectNode parentNode = grandparentNode.findOrCreateChildNode(parent);
        ObjectNode oldChildParent = getParentNode(child);
        boolean childNodeWasNew = oldChildParent.findChildNode(child) == null;
        ObjectNode childNode = oldChildParent.moveChildNodeToOtherParent(child, parentNode);
        myObject2ParentNode.put(child, parentNode);

        assert childNode.getObject() == child;
        checkWasNotAddedAlreadyAsChild(parentNode, childNode);

        // parent could be disposed outside our lock while we are messing with the pointers here - undo registering in this case
        if (isDisposed(parent)) {
          Throwable trace = Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
          toDispose = new ArrayList<>();
          childNode.removeChildNodesRecursively(toDispose, this, trace, null);
          for (Disposable disposable : toDispose) {
            myObject2ParentNode.remove(disposable);
          }
          if (childNodeWasNew) {
            parentNode.removeChildNode(childNode);
            myObject2ParentNode.remove(child);
            if (rememberDisposedTrace(child, trace) == null) {
              toDispose.add(child);
            }
          }
          else {
            parentNode.moveChildNodeToOtherParent(child, oldChildParent);
            if (oldChildParent == myRootNode) {
              // Root-level disposables aren't tracked in myObject2ParentNode.
              myObject2ParentNode.remove(child);
            }
            else {
              myObject2ParentNode.put(child, oldChildParent);
            }
          }

          if (parentNodeWasNew) {
            grandparentNode.removeChildNode(parentNode);
          }

          // mark as disposed inside the tree lock
          for (Disposable disposable : toDispose) {
            if (disposable instanceof Disposer.CheckedDisposableImpl) {
              ((Disposer.CheckedDisposableImpl)disposable).isDisposed = true;
            }
          }
        }
        else {
          success = true;
        }
      }
    }
    // must be called outside tree lock
    if (toDispose != null && !toDispose.isEmpty()) {
      disposeStrandedList(toDispose);
    }
    return success;
  }


  // Must be called with tree lock held.
  private @NotNull List<Disposable> collectStrandedChild(@NotNull Disposable child) {
    ObjectNode currentParent = getParentNode(child);
    ObjectNode childNode = currentParent.findChildNode(child);
    if (childNode == null) {
      // child never made it into the tree - nothing to clean up.
      return Collections.emptyList();
    }
    Throwable trace = Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
    List<Disposable> collected = new ArrayList<>();
    childNode.removeChildNodesRecursively(collected, this, trace, null);
    currentParent.removeChildNode(childNode);
    for (Disposable descendant : collected) {
      myObject2ParentNode.remove(descendant);
    }
    myObject2ParentNode.remove(child);
    if (rememberDisposedTrace(child, trace) == null) {
      collected.add(child);
    }
    for (Disposable disposable : collected) {
      if (disposable instanceof Disposer.CheckedDisposableImpl) {
        ((Disposer.CheckedDisposableImpl)disposable).isDisposed = true;
      }
    }
    return collected;
  }

   // Must be called outside tree lock.
  private static void disposeStrandedList(@NotNull List<Disposable> toDispose) {
    List<Throwable> exceptions = null;
    // beforeTreeDispose in pre-order - some clients are hardcoded to see parents-then-children order
    for (int i = toDispose.size() - 1; i >= 0; i--) {
      Disposable disposable = toDispose.get(i);
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
    // dispose in post-order (bottom-up)
    for (Disposable disposable : toDispose) {
      try {
        //noinspection SSBasedInspection
        disposable.dispose();
      }
      catch (Throwable e) {
        if (exceptions == null) exceptions = new SmartList<>();
        exceptions.add(e);
      }
    }
    if (exceptions != null) {
      handleExceptions(exceptions);
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
    try {
      // second/third: beforeTreeDispose in pre-order, dispose in post-order (bottom-up).
      disposeStrandedList(disposables);
    }
    finally {
      if (needTrace) {
        ourTopmostDisposeTrace.remove();
      }
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
      
      // Mark CheckedDisposable objects as disposed INSIDE the lock to prevent race condition
      // where another thread could re-register the disposable between tree removal and dispose() call.
      for (Disposable disposable : disposables) {
        if (disposable instanceof Disposer.CheckedDisposableImpl) {
          ((Disposer.CheckedDisposableImpl)disposable).isDisposed = true;
        }
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

      // Mark CheckedDisposable objects as disposed INSIDE the lock to prevent race condition
      // where another thread could re-register the disposable between tree removal and dispose() call.
      // This is safe because CheckedDisposable.dispose() is idempotent (just sets isDisposed=true).
      for (Disposable disposable : disposables) {
        if (disposable instanceof Disposer.CheckedDisposableImpl) {
          ((Disposer.CheckedDisposableImpl)disposable).isDisposed = true;
        }
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
      // Walk from myRootNode so root-level orphans (a Disposable that lives as a direct child
      // of the tree root without any tracked descendants in myObject2ParentNode) are also covered.
      myRootNode.assertNoReferencesKept(disposable);
    }
  }

  @TestOnly
  public void assertNoReferenceKeptInTree(@NotNull Class<Disposable> disposableClass) {
    synchronized (getTreeLock()) {
      myRootNode.assertNoReferencesKept(disposableClass);
    }
  }

  /**
   * Asserts that no objects matching the given predicate are reachable in the Tree.
   *
   * @param predicate the predicate to test objects against; returns {@code true} for objects that should not be present
   * @throws AssertionError if any object matching the predicate is found reachable from any node in the tree
   */
  @TestOnly
  public void assertNoReferenceKeptInTree(@NotNull Predicate<Object> predicate) {
    synchronized (getTreeLock()) {
      myRootNode.assertNoReferencesKept(predicate);
    }
  }

  @TestOnly
  public @Nullable String printParentChainToRoot(@NotNull Disposable disposable) {
    synchronized (getTreeLock()) {
      ObjectNode parentNode = myObject2ParentNode.get(disposable);
      if (parentNode == null && myRootNode.findChildNode(disposable) == null) {
        return null;
      }
      StringBuilder sb = new StringBuilder();
      sb.append("  ").append(disposable).append(" (").append(disposable.getClass().getName()).append(')');
      for (ObjectNode current = parentNode;
           current != null && current != myRootNode;
           current = myObject2ParentNode.get(current.getObject())) {
        Disposable p = current.getObject();
        sb.append("\n    <- ").append(p).append(" (").append(p.getClass().getName()).append(')');
      }
      sb.append("\n    <- ROOT");
      return sb.toString();
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
