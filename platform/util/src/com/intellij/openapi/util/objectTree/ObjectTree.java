/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.Equality;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class ObjectTree<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");

  private final List<ObjectTreeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<T> myRootObjects = ContainerUtil.newIdentityTroveSet();
  private final Map<T, ObjectNode<T>> myObject2NodeMap = ContainerUtil.newIdentityTroveMap();

  private final List<ObjectNode<T>> myExecutedNodes = new ArrayList<ObjectNode<T>>();
  private final List<T> myExecutedUnregisteredNodes = new ArrayList<T>();

  final Object treeLock = new Object();

  private long myModification;

  public ObjectNode<T> getNode(@NotNull T object) {
    synchronized (treeLock) {
      return myObject2NodeMap.get(object);
    }
  }
  public ObjectNode<T> putNode(@NotNull T object, @Nullable("null means remove") ObjectNode<T> node) {
    synchronized (treeLock) {
      return node == null ? myObject2NodeMap.remove(object) : myObject2NodeMap.put(object, node);
    }
  }

  public final List<ObjectNode<T>> getNodesInExecution() {
    return myExecutedNodes;
  }

  public final void register(@NotNull T parent, @NotNull T child) {
    synchronized (treeLock) {
      ObjectNode<T> parentNode = getOrCreateNodeFor(parent, null);

      ObjectNode<T> childNode = getNode(child);
      if (childNode == null) {
        childNode = createNodeFor(child, parentNode, Disposer.isDebugMode() ? new Throwable() : null);
      }
      else {
        ObjectNode<T> oldParent = childNode.getParent();
        if (oldParent != null) {
          oldParent.removeChild(childNode);
        }
      }
      myRootObjects.remove(child);
      checkWasNotAddedAlready(childNode, child);
      parentNode.addChild(childNode);

      fireRegistered(childNode.getObject());
    }
  }

  private void checkWasNotAddedAlready(@NotNull ObjectNode<T> childNode, @NotNull T child) {
    ObjectNode parent = childNode.getParent();
    boolean childIsInTree = parent != null;
    if (!childIsInTree) return;

    while (parent != null) {
      if (parent.getObject() == child) {
        LOG.error(child + " was already added as a child of: " + parent);
      }
      parent = parent.getParent();
    }
  }

  @NotNull
  private ObjectNode<T> getOrCreateNodeFor(@NotNull T object, @Nullable ObjectNode<T> defaultParent) {
    final ObjectNode<T> node = getNode(object);

    if (node != null) return node;

    return createNodeFor(object, defaultParent, Disposer.isDebugMode() ? new Throwable() : null);
  }

  @NotNull
  private ObjectNode<T> createNodeFor(@NotNull T object, @Nullable ObjectNode<T> parentNode, @Nullable final Throwable trace) {
    final ObjectNode<T> newNode = new ObjectNode<T>(this, parentNode, object, getNextModification(),
                                                    trace);
    if (parentNode == null) {
      myRootObjects.add(object);
    }
    putNode(object, newNode);
    return newNode;
  }

  public long getNextModification() {
    return ++myModification;
  }

  public final boolean executeAll(@NotNull T object, boolean disposeTree, @NotNull ObjectTreeAction<T> action, boolean processUnregistered) {
    ObjectNode<T> node = getNode(object);
    if (node == null) {
      if (processUnregistered) {
        executeUnregistered(object, action);
        return true;
      }
      else {
        return false;
      }
    }
    node.execute(disposeTree, action);
    return true;
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  static <T> void executeActionWithRecursiveGuard(@NotNull T object,
                                                  @NotNull List<T> recursiveGuard,
                                                  @NotNull final ObjectTreeAction<T> action) {
    synchronized (recursiveGuard) {
      if (ArrayUtil.indexOf(recursiveGuard, object, Equality.IDENTITY) != -1) return;
      recursiveGuard.add(object);
    }

    try {
      action.execute(object);
    }
    finally {
      synchronized (recursiveGuard) {
        int i = ArrayUtil.indexOf(recursiveGuard, object, Equality.IDENTITY);
        assert i != -1;
        recursiveGuard.remove(i);
      }
    }
  }

  private void executeUnregistered(@NotNull final T object, @NotNull final ObjectTreeAction<T> action) {
    executeActionWithRecursiveGuard(object, myExecutedUnregisteredNodes, action);
  }

  public final void executeChildAndReplace(@NotNull T toExecute, @NotNull T toReplace, boolean disposeTree, @NotNull ObjectTreeAction<T> action) {
    final ObjectNode<T> toExecuteNode = getNode(toExecute);
    assert toExecuteNode != null : "Object " + toExecute + " wasn't registered or already disposed";

    T parentObject;
    synchronized (treeLock) {
      final ObjectNode<T> parent = toExecuteNode.getParent();
      assert parent != null : "Object " + toExecute + " is not connected to the tree - doesn't have parent";
      parentObject = parent.getObject();
    }

    toExecuteNode.execute(disposeTree, action);
    register(parentObject, toReplace);
  }

  public boolean containsKey(@NotNull T object) {
    return getNode(object) != null;
  }

  @TestOnly
  public void assertNoReferenceKeptInTree(@NotNull T disposable) {
    Collection<ObjectNode<T>> nodes = myObject2NodeMap.values();
    for (ObjectNode<T> node : nodes) {
      node.assertNoReferencesKept(disposable);
    }
  }

  public void removeRootObject(@NotNull T object) {
    synchronized (treeLock) {
      myRootObjects.remove(object);
    }
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  public void assertIsEmpty(boolean throwError) {
    for (T object : myRootObjects) {
      if (object == null) continue;
      final ObjectNode<T> objectNode = getNode(object);
      if (objectNode == null) continue;

      final Throwable trace = objectNode.getTrace();
      RuntimeException exception = new RuntimeException("Memory leak detected: " + object + " of class " + object.getClass()
                                                        + "\nSee the cause for the corresponding Disposer.register() stacktrace:\n",
                                                        trace);
      if (throwError) {
        throw exception;
      }
      LOG.error(exception);
    }
  }
  
  @TestOnly
  public boolean isEmpty() {
    return myRootObjects.isEmpty();
  }

  @TestOnly
  public void clearAll() {
    myRootObjects.clear();
    myExecutedNodes.clear();
    myExecutedUnregisteredNodes.clear();
    myObject2NodeMap.clear();
  }

  @NotNull
  public Set<T> getRootObjects() {
    return myRootObjects;
  }

  public void addListener(@NotNull ObjectTreeListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ObjectTreeListener listener) {
    myListeners.remove(listener);
  }

  void fireRegistered(@NotNull Object object) {
    for (ObjectTreeListener each : myListeners) {
      each.objectRegistered(object);
    }
  }

  void fireExecuted(@NotNull Object object) {
    for (ObjectTreeListener each : myListeners) {
      each.objectExecuted(object);
    }
  }

  public int size() {
    return myObject2NodeMap.size();
  }

  @Nullable
  public <D extends Disposable> D findRegisteredObject(@NotNull T parentDisposable, @NotNull D object) {
    synchronized (treeLock) {
      ObjectNode<T> parentNode = getNode(parentDisposable);
      if (parentNode == null) return null;
      return parentNode.findChildEqualTo(object);
    }
  }

  public long getModification() {
    return myModification;
  }
}
