// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ObjectTree {
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();
  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Map<Disposable, ObjectNode> myObject2NodeMap = new Reference2ObjectOpenHashMap<>(); // guarded by treeLock
  // Disposable -> trace or boolean marker (if trace unavailable)
  private final Map<Disposable, Object> myDisposedObjects = CollectionFactory.createWeakIdentityMap(100, 0.5f); // guarded by treeLock

  private final Object treeLock = new Object();
  private final ObjectNode ROOT_NODE = ObjectNode.createRoot(); // root objects are stored in this node children

  private ObjectNode getNode(@NotNull Disposable object) {
    return myObject2NodeMap.get(object);
  }

  @Nullable RuntimeException register(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) {
      return new IllegalArgumentException("Cannot register to itself: "+parent);
    }
    synchronized (treeLock) {
      Object wasDisposed = getDisposalInfo(parent);
      if (wasDisposed != null) {
        return new IncorrectOperationException("Sorry but parent: " + parent + " (" + parent.getClass()+") has already been disposed " +
                                              "(see the cause for stacktrace) so the child: "+child+ " (" + child.getClass()+") will never be disposed",
                                              wasDisposed instanceof Throwable ? (Throwable)wasDisposed : null);
      }

      myDisposedObjects.remove(child); // if we dispose thing and then register it back it means it's not disposed anymore
      if (child instanceof Disposer.CheckedDisposableImpl) {
        ((Disposer.CheckedDisposableImpl)child).isDisposed = false;
      }
      ObjectNode parentNode = getOrCreateParentNode(parent);
      ObjectNode childNode = getOrCreateChildNode(parentNode, child);

      RuntimeException e = checkWasNotAddedAlreadyAsChild(parentNode, childNode);
      if (e != null) return e;

      parentNode.addChild(childNode);
    }
    return null;
  }

  Object getDisposalInfo(@NotNull Disposable object) {
    synchronized (treeLock) {
      return myDisposedObjects.get(object);
    }
  }

  private RuntimeException checkWasNotAddedAlreadyAsChild(@NotNull ObjectNode childNode, @NotNull ObjectNode parentNode) {
    for (ObjectNode node = childNode; node != ROOT_NODE; node = node.getParent()) {
      if (node == parentNode) {
        return new IncorrectOperationException("'"+childNode.getObject() + "' was already added as a child of '" + parentNode.getObject()+"'");
      }
    }
    return null;
  }

  @NotNull
  private ObjectNode getOrCreateParentNode(@NotNull Disposable parent) {
    return myObject2NodeMap.computeIfAbsent(parent, p -> new ObjectNode(p, ROOT_NODE));
  }
  @NotNull
  private ObjectNode getOrCreateChildNode(@NotNull ObjectNode parentNode, @NotNull Disposable child) {
    return myObject2NodeMap.compute(child, (c, oldNode) -> {
      if (oldNode == null) {
        oldNode = new ObjectNode(c, parentNode);
      }
      else {
        ObjectNode oldParent = oldNode.getParent();
        oldParent.removeChild(oldNode);
      }
      return oldNode;
    });
  }


  private void runWithTrace(@NotNull Supplier<? extends @NotNull List<Disposable>> removeFromTreeAction) {
    boolean needTrace = Disposer.isDebugMode() && ourTopmostDisposeTrace.get() == null;
    if (needTrace) {
      ourTopmostDisposeTrace.set(ThrowableInterner.intern(new Throwable()));
    }

    // first, atomically remove disposables from the tree to avoid "register during dispose" race conditions
    List<Disposable> disposables;
    synchronized (treeLock) {
      disposables = removeFromTreeAction.get();
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
    for (Disposable disposable : disposables) {
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

  void executeAllChildren(@NotNull Disposable object, @Nullable Predicate<? super Disposable> predicate) {
    runWithTrace(() -> {
      ObjectNode node = getNode(object);
      if (node == null) {
        return Collections.emptyList();
      }

      List<Disposable> disposables = new ArrayList<>();
      node.getAndRemoveChildrenRecursively(this, disposables, predicate);
      return disposables;
    });
  }

  void executeAll(@NotNull Disposable object, boolean processUnregistered) {
    runWithTrace(() -> {
      ObjectNode node = getNode(object);
      if (node == null && !processUnregistered) {
        return Collections.emptyList();
      }
      List<Disposable> disposables = new ArrayList<>();
      if (node == null) {
        if (rememberDisposedTrace(object) == null) {
          disposables.add(object);
        }
      }
      else {
        node.getAndRemoveRecursively(this, disposables);
      }
      return disposables;
    });
  }

  private static void handleExceptions(@NotNull List<? extends Throwable> exceptions) {
    if (exceptions.isEmpty()) {
      return;
    }

    ProcessCanceledException processCanceledException = null;
    for (Throwable exception : exceptions) {
      if (!(exception instanceof ProcessCanceledException)) {
        getLogger().error(exception);
      }
      else if (processCanceledException == null) {
        processCanceledException = (ProcessCanceledException)exception;
      }
    }

    if (processCanceledException != null) {
      throw processCanceledException;
    }
  }

  @TestOnly
  void assertNoReferenceKeptInTree(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      for (ObjectNode node : myObject2NodeMap.values()) {
        node.assertNoReferencesKept(disposable);
      }
    }
  }

  void assertIsEmpty(boolean throwError) {
    synchronized (treeLock) {
      ROOT_NODE.assertNoChildren(throwError);
    }
  }

  Throwable getRegistrationTrace(@NotNull Disposable object) {
    ObjectNode objectNode = getNode(object);
    return objectNode == null ? null : objectNode.getTrace();
  }

  @NotNull
  static Logger getLogger() {
    return Logger.getInstance(ObjectTree.class);
  }

  // return old value
  Object rememberDisposedTrace(@NotNull Disposable object) {
    synchronized (treeLock) {
      Throwable trace = ourTopmostDisposeTrace.get();
      return myDisposedObjects.put(object, trace != null ? trace : Boolean.TRUE);
    }
  }

  void clearDisposedObjectTraces() {
    synchronized (treeLock) {
      myDisposedObjects.clear();
      for (ObjectNode value : myObject2NodeMap.values()) {
        value.clearTrace();
      }
    }
  }

  void removeObjectFromTree(@NotNull ObjectNode node) {
    synchronized (treeLock) {
      Disposable myObject = node.getObject();
      myObject2NodeMap.remove(myObject);
      ObjectNode parent = node.getParent();
      parent.removeChild(node);
    }
  }
}
