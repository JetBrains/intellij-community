// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ObjectTree {
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<Disposable> myRootObjects = new ReferenceOpenHashSet<>(); // guarded by treeLock
  // guarded by treeLock
  private final Map<Disposable, ObjectNode> myObject2NodeMap = new Reference2ObjectOpenHashMap<>();
  // Disposable to trace or boolean marker (if trace unavailable)
  private final Map<Disposable, Object> myDisposedObjects = ContainerUtil.createWeakMap(100, 0.5f, ContainerUtil.identityStrategy()); // guarded by treeLock

  private final List<ObjectNode> myExecutedNodes = new ArrayList<>(); // guarded by myExecutedNodes
  private final List<Disposable> myExecutedUnregisteredObjects = new ArrayList<>(); // guarded by myExecutedUnregisteredObjects

  final Object treeLock = new Object();

  private ObjectNode getNode(@NotNull Disposable object) {
    return myObject2NodeMap.get(object);
  }

  void putNode(@NotNull Disposable object, @Nullable("null means remove") ObjectNode node) {
    if (node == null) {
      myObject2NodeMap.remove(object);
    }
    else {
      myObject2NodeMap.put(object, node);
    }
  }

  @NotNull
  final List<ObjectNode> getNodesInExecution() {
    return myExecutedNodes;
  }

  final void register(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) throw new IllegalArgumentException("Cannot register to itself: "+parent);
    synchronized (treeLock) {
      Object wasDisposed = getDisposalInfo(parent);
      if (wasDisposed != null) {
        throw new IncorrectOperationException("Sorry but parent: " + parent + " has already been disposed " +
                                              "(see the cause for stacktrace) so the child: "+child+" will never be disposed",
                                              wasDisposed instanceof Throwable ? (Throwable)wasDisposed : null);
      }

      if (isDisposing(parent)) {
        throw new IncorrectOperationException("Sorry but parent: " + parent + " is being disposed so the child: "+child+" will never be disposed");
      }

      myDisposedObjects.remove(child); // if we dispose thing and then register it back it means it's not disposed anymore
      ObjectNode parentNode = getNode(parent);
      if (parentNode == null) parentNode = createNodeFor(parent, null);

      ObjectNode childNode = getNode(child);
      if (childNode == null) {
        childNode = createNodeFor(child, parentNode);
      }
      else {
        ObjectNode oldParent = childNode.getParent();
        if (oldParent != null) {
          oldParent.removeChild(childNode);
        }
      }
      myRootObjects.remove(child);

      checkWasNotAddedAlready(parentNode, childNode);

      parentNode.addChild(childNode);
    }
  }

  Object getDisposalInfo(@NotNull Disposable object) {
    synchronized (treeLock) {
      return myDisposedObjects.get(object);
    }
  }

  private static void checkWasNotAddedAlready(@NotNull ObjectNode childNode, @NotNull ObjectNode parentNode) {
    for (ObjectNode node = childNode; node != null; node = node.getParent()) {
      if (node == parentNode) {
        throw new IncorrectOperationException("'"+childNode.getObject() + "' was already added as a child of '" + parentNode.getObject()+"'");
      }
    }
  }

  @NotNull
  private ObjectNode createNodeFor(@NotNull Disposable object, @Nullable ObjectNode parentNode) {
    final ObjectNode newNode = new ObjectNode(this, parentNode, object);
    if (parentNode == null) {
      myRootObjects.add(object);
    }
    putNode(object, newNode);
    return newNode;
  }

  final void executeAll(@NotNull Disposable object, boolean processUnregistered, boolean onlyChildren) {
    ObjectNode node;
    synchronized (treeLock) {
      node = getNode(object);
    }
    boolean needTrace = (node != null || processUnregistered) && Disposer.isDebugMode() && ourTopmostDisposeTrace.get() == null;
    if (needTrace) {
      ourTopmostDisposeTrace.set(ThrowableInterner.intern(new Throwable()));
    }

    try {
      if (node == null) {
        if (processUnregistered) {
          rememberDisposedTrace(object);
          executeUnregistered(object);
        }
      }
      else {
        ObjectNode parent = node.getParent();
        List<Throwable> exceptions = new SmartList<>();
        node.execute(exceptions, onlyChildren);
        if (parent != null && !onlyChildren) {
          synchronized (treeLock) {
            parent.removeChild(node);
          }
        }
        handleExceptions(exceptions);
      }
    }
    finally {
      if (needTrace) {
        ourTopmostDisposeTrace.remove();
      }
    }
  }

  private static void handleExceptions(@NotNull List<? extends Throwable> exceptions) {
    if (!exceptions.isEmpty()) {
      for (Throwable exception : exceptions) {
        if (!(exception instanceof ProcessCanceledException)) {
          getLogger().error(exception);
        }
      }

      ProcessCanceledException pce = ContainerUtil.findInstance(exceptions, ProcessCanceledException.class);
      if (pce != null) {
        throw pce;
      }
    }
  }

  public boolean isDisposing(@NotNull Disposable disposable) {
    List<ObjectNode> guard = getNodesInExecution();
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (guard) {
      for (ObjectNode node : guard) {
        if (node.getObject() == disposable) return true;
      }
    }
    return false;
  }

  static <T> void executeActionWithRecursiveGuard(@NotNull T object, @NotNull List<T> recursiveGuard, @NotNull Consumer<? super T> action) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (recursiveGuard) {
      if (ArrayUtil.indexOf(recursiveGuard, object, (t, t2) -> t == t2) != -1) {
        return;
      }
      recursiveGuard.add(object);
    }

    try {
      action.accept(object);
    }
    finally {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (recursiveGuard) {
        int i = ArrayUtil.lastIndexOf(recursiveGuard, object, (t, t2) -> t == t2);
        assert i != -1;
        recursiveGuard.remove(i);
      }
    }
  }

  private void executeUnregistered(@NotNull Disposable disposable) {
    executeActionWithRecursiveGuard(disposable, myExecutedUnregisteredObjects, Disposable::dispose);
  }

  @TestOnly
  void assertNoReferenceKeptInTree(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      for (Map.Entry<Disposable, ObjectNode> entry : myObject2NodeMap.entrySet()) {
        Disposable key = entry.getKey();
        assert key != disposable;
        ObjectNode node = entry.getValue();
        node.assertNoReferencesKept(disposable);
      }
    }
  }

  void removeRootObject(@NotNull Disposable object) {
    myRootObjects.remove(object);
  }

  void assertIsEmpty(boolean throwError) {
    synchronized (treeLock) {
      for (Disposable object : myRootObjects) {
        if (object == null) continue;
        ObjectNode objectNode = getNode(object);
        if (objectNode == null) continue;
        while (objectNode.getParent() != null) {
          objectNode = objectNode.getParent();
        }
        final Throwable trace = objectNode.getTrace();
        RuntimeException exception = new RuntimeException("Memory leak detected: '" + object + "' of " + object.getClass()
                                                          + "\nSee the cause for the corresponding Disposer.register() stacktrace:\n",
                                                          trace);
        if (throwError) {
          throw exception;
        }
        getLogger().error(exception);
      }
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(ObjectTree.class);
  }

  void rememberDisposedTrace(@NotNull Disposable object) {
    synchronized (treeLock) {
      Throwable trace = ourTopmostDisposeTrace.get();
      myDisposedObjects.put(object, trace != null ? trace : Boolean.TRUE);
    }
  }

  void clearDisposedObjectTraces() {
    myDisposedObjects.clear();
    synchronized (treeLock) {
      for (ObjectNode value : myObject2NodeMap.values()) {
        value.clearTrace();
      }
    }
  }

  @Nullable
  <D extends Disposable> D findRegisteredObject(@NotNull Disposable parentDisposable, @NotNull D object) {
    synchronized (treeLock) {
      ObjectNode parentNode = getNode(parentDisposable);
      if (parentNode == null) return null;
      return parentNode.findChildEqualTo(object);
    }
  }

}
