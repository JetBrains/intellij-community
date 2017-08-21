/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ObjectTree<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");

  private final List<ObjectTreeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<T> myRootObjects = ContainerUtil.newIdentityTroveSet(); // guarded by treeLock
  private final Map<T, ObjectNode<T>> myObject2NodeMap = ContainerUtil.newIdentityTroveMap(); // guarded by treeLock
  private final Map<Object, Object> myDisposedObjects = ContainerUtil.createWeakMap(100, 0.5f, ContainerUtil.identityStrategy()); // guarded by treeLock

  private final List<ObjectNode<T>> myExecutedNodes = new ArrayList<ObjectNode<T>>(); // guarded by myExecutedNodes
  private final List<T> myExecutedUnregisteredNodes = new ArrayList<T>(); // guarded by myExecutedUnregisteredNodes

  final Object treeLock = new Object();

  private final AtomicLong myModification = new AtomicLong(0);

  ObjectNode<T> getNode(@NotNull T object) {
    return myObject2NodeMap.get(object);
  }

  ObjectNode<T> putNode(@NotNull T object, @Nullable("null means remove") ObjectNode<T> node) {
    return node == null ? myObject2NodeMap.remove(object) : myObject2NodeMap.put(object, node);
  }

  @NotNull
  final List<ObjectNode<T>> getNodesInExecution() {
    return myExecutedNodes;
  }

  public final void register(@NotNull T parent, @NotNull T child) {
    if (parent == child) throw new IllegalArgumentException("Cannot register to itself: "+parent);
    Object wasDisposed = getDisposalInfo(parent);
    if (wasDisposed != null) {
      throw new IncorrectOperationException("Sorry but parent: " + parent + " has already been disposed " +
                                            "(see the cause for stacktrace) so the child: "+child+" will never be disposed",
                                            wasDisposed instanceof Throwable ? (Throwable)wasDisposed : null);
    }

    synchronized (treeLock) {
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

      fireRegistered(childNode.getObject());
    }
  }

  public Object getDisposalInfo(@NotNull T parent) {
    synchronized (treeLock) {
      return myDisposedObjects.get(parent);
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
    final ObjectNode<T> newNode = new ObjectNode<T>(this, parentNode, object, getNextModification());
    if (parentNode == null) {
      myRootObjects.add(object);
    }
    putNode(object, newNode);
    return newNode;
  }

  private long getNextModification() {
    return myModification.incrementAndGet();
  }

  public final boolean executeAll(@NotNull T object, @NotNull ObjectTreeAction<T> action, boolean processUnregistered) {
    ObjectNode<T> node;
    synchronized (treeLock) {
      node = getNode(object);
    }
    if (node == null) {
      if (processUnregistered) {
        rememberDisposedTrace(object);
        executeUnregistered(object, action);
        return true;
      }
      return false;
    }
    node.execute(action);
    return true;
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  static <T> void executeActionWithRecursiveGuard(@NotNull T object,
                                                  @NotNull List<T> recursiveGuard,
                                                  @NotNull final ObjectTreeAction<T> action) {
    synchronized (recursiveGuard) {
      if (ArrayUtil.indexOf(recursiveGuard, object, ContainerUtil.<T>identityStrategy()) != -1) return;
      recursiveGuard.add(object);
    }

    try {
      action.execute(object);
    }
    finally {
      synchronized (recursiveGuard) {
        int i = ArrayUtil.lastIndexOf(recursiveGuard, object, ContainerUtil.<T>identityStrategy());
        assert i != -1;
        recursiveGuard.remove(i);
      }
    }
  }

  private void executeUnregistered(@NotNull final T object, @NotNull final ObjectTreeAction<T> action) {
    executeActionWithRecursiveGuard(object, myExecutedUnregisteredNodes, action);
  }

  public final void executeChildAndReplace(@NotNull T toExecute,
                                           @NotNull T toReplace,
                                           @NotNull ObjectTreeAction<T> action) {
    final ObjectNode<T> toExecuteNode;
    T parentObject;
    synchronized (treeLock) {
      toExecuteNode = getNode(toExecute);
      if (toExecuteNode == null) throw new IllegalArgumentException("Object " + toExecute + " wasn't registered or already disposed");

      final ObjectNode<T> parent = toExecuteNode.getParent();
      if (parent == null) throw new IllegalArgumentException("Object " + toExecute + " is not connected to the tree - doesn't have parent");
      parentObject = parent.getObject();
    }

    toExecuteNode.execute(action);
    register(parentObject, toReplace);
  }

  public boolean containsKey(@NotNull T object) {
    synchronized (treeLock) {
      return getNode(object) != null;
    }
  }

  @TestOnly
  // public for Upsource
  public void assertNoReferenceKeptInTree(@NotNull T disposable) {
    synchronized (treeLock) {
      Collection<ObjectNode<T>> nodes = myObject2NodeMap.values();
      for (ObjectNode<T> node : nodes) {
        node.assertNoReferencesKept(disposable);
      }
    }
  }

  void removeRootObject(@NotNull T object) {
    myRootObjects.remove(object);
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
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
        LOG.error(exception);
      }
    }
  }
  
  @TestOnly
  public boolean isEmpty() {
    synchronized (treeLock) {
      return myRootObjects.isEmpty();
    }
  }

  @NotNull
  Set<T> getRootObjects() {
    synchronized (treeLock) {
      return myRootObjects;
    }
  }

  void addListener(@NotNull ObjectTreeListener listener) {
    myListeners.add(listener);
  }

  void removeListener(@NotNull ObjectTreeListener listener) {
    myListeners.remove(listener);
  }

  private void fireRegistered(@NotNull Object object) {
    for (ObjectTreeListener each : myListeners) {
      each.objectRegistered(object);
    }
  }

  void fireExecuted(@NotNull Object object) {
    for (ObjectTreeListener each : myListeners) {
      each.objectExecuted(object);
    }
    rememberDisposedTrace(object);
  }

  private void rememberDisposedTrace(@NotNull Object object) {
    synchronized (treeLock) {
      myDisposedObjects.put(object, Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : Boolean.TRUE);
    }
  }

  int size() {
    synchronized (treeLock) {
      return myObject2NodeMap.size();
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

  long getModification() {
    return myModification.get();
  }
}
