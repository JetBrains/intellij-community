// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ObjectTree<T extends Disposable> {
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<T> myRootObjects = ContainerUtil.newIdentityTroveSet(); // guarded by treeLock
  private final Map<T, ObjectNode<T>> myObject2NodeMap = ContainerUtil.newIdentityTroveMap(); // guarded by treeLock
  // Disposable to trace or boolean marker (if trace unavailable)
  private final Map<T, Object> myDisposedObjects = ContainerUtil.createWeakMap(100, 0.5f, ContainerUtil.identityStrategy()); // guarded by treeLock

  private final List<ObjectNode<T>> myExecutedNodes = new ArrayList<>(); // guarded by myExecutedNodes
  private final List<T> myExecutedUnregisteredObjects = new ArrayList<>(); // guarded by myExecutedUnregisteredObjects

  final Object treeLock = new Object();

  private ObjectNode<T> getNode(@NotNull T object) {
    return myObject2NodeMap.get(object);
  }

  void putNode(@NotNull T object, @Nullable("null means remove") ObjectNode<T> node) {
    if (node == null) {
      myObject2NodeMap.remove(object);
    }
    else {
      myObject2NodeMap.put(object, node);
    }
  }

  @NotNull
  final List<ObjectNode<T>> getNodesInExecution() {
    return myExecutedNodes;
  }

  public final void register(@NotNull T parent, @NotNull T child) {
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
      ObjectNode<T> parentNode = getNode(parent);
      if (parentNode == null) parentNode = createNodeFor(parent, null);

      ObjectNode<T> childNode = getNode(child);
      if (childNode == null) {
        childNode = createNodeFor(child, parentNode);
      }
      else {
        ObjectNode<T> oldParent = childNode.getParent();
        if (oldParent != null) {
          oldParent.removeChild(childNode);
        }
      }
      myRootObjects.remove(child);

      checkWasNotAddedAlready(parentNode, childNode);

      parentNode.addChild(childNode);
    }
  }

  public Object getDisposalInfo(@NotNull T object) {
    synchronized (treeLock) {
      return myDisposedObjects.get(object);
    }
  }

  private void checkWasNotAddedAlready(ObjectNode<T> childNode, @NotNull ObjectNode<T> parentNode) {
    for (ObjectNode<T> node = childNode; node != null; node = node.getParent()) {
      if (node == parentNode) {
        throw new IncorrectOperationException("'"+childNode.getObject() + "' was already added as a child of '" + parentNode.getObject()+"'");
      }
    }
  }

  @NotNull
  private ObjectNode<T> createNodeFor(@NotNull T object, @Nullable ObjectNode<T> parentNode) {
    final ObjectNode<T> newNode = new ObjectNode<>(this, parentNode, object);
    if (parentNode == null) {
      myRootObjects.add(object);
    }
    putNode(object, newNode);
    return newNode;
  }

  public final void executeAll(@NotNull T object, @NotNull ObjectTreeAction<T> action, boolean processUnregistered) {
    ObjectNode<T> node;
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
          executeUnregistered(object, action);
        }
      }
      else {
        ObjectNode<T> parent = node.getParent();
        List<Throwable> exceptions = new SmartList<>();
        node.execute(action, exceptions);
        if (parent != null) {
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

  private static void handleExceptions(List<? extends Throwable> exceptions) {
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

  public boolean isDisposing(@NotNull T disposable) {
    List<ObjectNode<T>> guard = getNodesInExecution();
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (guard) {
      for (ObjectNode<T> node : guard) {
        if (node.getObject() == disposable) return true;
      }
    }
    return false;
  }

  static <T> void executeActionWithRecursiveGuard(@NotNull T object,
                                                  @NotNull List<T> recursiveGuard,
                                                  @NotNull final ObjectTreeAction<? super T> action) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (recursiveGuard) {
      if (ArrayUtil.indexOf(recursiveGuard, object, ContainerUtil.identityStrategy()) != -1) return;
      recursiveGuard.add(object);
    }

    try {
      action.execute(object);
    }
    finally {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (recursiveGuard) {
        int i = ArrayUtil.lastIndexOf(recursiveGuard, object, ContainerUtil.identityStrategy());
        assert i != -1;
        recursiveGuard.remove(i);
      }
    }
  }

  private void executeUnregistered(@NotNull final T object, @NotNull final ObjectTreeAction<? super T> action) {
    executeActionWithRecursiveGuard(object, myExecutedUnregisteredObjects, action);
  }

  @TestOnly
  void assertNoReferenceKeptInTree(@NotNull T disposable) {
    synchronized (treeLock) {
      for (Map.Entry<T, ObjectNode<T>> entry : myObject2NodeMap.entrySet()) {
        T key = entry.getKey();
        assert key != disposable;
        ObjectNode<T> node = entry.getValue();
        node.assertNoReferencesKept(disposable);
      }
    }
  }

  void removeRootObject(@NotNull T object) {
    myRootObjects.remove(object);
  }

  public void assertIsEmpty(boolean throwError) {
    synchronized (treeLock) {
      for (T object : myRootObjects) {
        if (object == null) continue;
        ObjectNode<T> objectNode = getNode(object);
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
    return Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");
  }

  void rememberDisposedTrace(@NotNull T object) {
    synchronized (treeLock) {
      Throwable trace = ourTopmostDisposeTrace.get();
      myDisposedObjects.put(object, trace != null ? trace : Boolean.TRUE);
    }
  }

  @Nullable
  public <D extends Disposable> D findRegisteredObject(@NotNull T parentDisposable, @NotNull D object) {
    synchronized (treeLock) {
      ObjectNode<T> parentNode = getNode(parentDisposable);
      if (parentNode == null) return null;
      return parentNode.findChildEqualTo(object);
    }
  }

}
