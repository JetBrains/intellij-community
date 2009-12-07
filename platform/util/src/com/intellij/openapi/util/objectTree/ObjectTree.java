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
import com.intellij.util.ArrayUtil;
import gnu.trove.Equality;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ObjectTree<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");

  private final CopyOnWriteArraySet<ObjectTreeListener> myListeners = new CopyOnWriteArraySet<ObjectTreeListener>();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final THashSet<T> myRootObjects = new MyTHashSet<T>();
  private final THashMap<T, ObjectNode<T>> myObject2NodeMap = new THashMap<T, ObjectNode<T>>(TObjectHashingStrategy.IDENTITY) {
    public void compact() {
      if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
        super.compact();
      }
    }
  };

  private final List<ObjectNode<T>> myExecutedNodes = new ArrayList<ObjectNode<T>>();
  private final List<T> myExecutedUnregisteredNodes = new ArrayList<T>();

  final Object treeLock = new Object();

  private long myModification;

  public ObjectNode<T> getNode(@NotNull T object) {
    synchronized (treeLock) {
      return myObject2NodeMap.get(object);
    }
  }
  public ObjectNode<T> putNode(@NotNull T object, ObjectNode<T> node) {
    synchronized (treeLock) {
      return node == null ? myObject2NodeMap.remove(object) : myObject2NodeMap.put(object, node);
    }
  }

  public final List<ObjectNode<T>> getNodesInExecution() {
    return myExecutedNodes;
  }

  public final void register(T parent, T child) {
    synchronized (treeLock) {
      final ObjectNode<T> childNode = getOrCreateNodeFor(child);
      checkValid(childNode, child);

      final ObjectNode<T> parentNode = getOrCreateNodeFor(parent);

      ObjectNode<T> childParent = childNode.getParent();
      if (childParent != parentNode && childParent != null) {
        childParent.removeChild(childNode);
        parentNode.addChild(childNode);
      }
      else if (myRootObjects.contains(child)) {
        parentNode.addChild(childNode);
        myRootObjects.remove(child);
      }
      else {
        parentNode.addChild(child);
      }

      fireRegistered(childNode.getObject());
    }
  }

  private void checkValid(ObjectNode<T> childNode, T child) {
    boolean childIsInTree = childNode != null && childNode.getParent() != null;
    if (!childIsInTree) return;

    ObjectNode eachParent = childNode.getParent();
    while (eachParent != null) {
      if (eachParent.getObject() == child) {
        LOG.error(child + " was already added as a child of: " + eachParent);
      }
      eachParent = eachParent.getParent();
    }
  }

  private ObjectNode<T> getOrCreateNodeFor(@NotNull T parentObject) {
    final ObjectNode<T> parentNode = getNode(parentObject);

    if (parentNode != null) return parentNode;

    final ObjectNode<T> parentless = new ObjectNode<T>(this, null, parentObject, getNextModification());
    myRootObjects.add(parentObject);
    putNode(parentObject, parentless);
    return parentless;
  }

  public long getNextModification() {
    return ++myModification;
  }

  public final boolean executeAll(@NotNull T object, boolean disposeTree, @NotNull ObjectTreeAction<T> action, boolean processUnregistered) {
    try {
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
      else {
        return node.execute(disposeTree, action);
      }
    }
    finally {
      synchronized (treeLock) {
        myObject2NodeMap.compact();
        myRootObjects.compact();
      }
    }
  }

  static <T> void executeActionWithRecursiveGuard(@NotNull T object, @NotNull final ObjectTreeAction<T> action, List<T> recursiveGuard) {
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
    executeActionWithRecursiveGuard(object, action, myExecutedUnregisteredNodes);
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
  public void assertNoReferenceKeptInTree(T disposable) {
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
  public void assertIsEmpty() {
    boolean firstObject = true;

    for (T object : myRootObjects) {
      if (object == null) continue;
      final ObjectNode<T> objectNode = getNode(object);
      if (objectNode == null) continue;

      if (firstObject) {
        firstObject = false;
        System.err.println("***********************************************************************************************");
        System.err.println("***                        M E M O R Y    L E A K S   D E T E C T E D                       ***");
        System.err.println("***********************************************************************************************");
        System.err.println("***                                                                                         ***");
        System.err.println("***   The following objects were not disposed: ");
      }

      System.err.println("***   " + object + " of class " + object.getClass());
      final Throwable trace = objectNode.getTrace();
      if (trace != null) {
        System.err.println("***         First seen at: ");
        trace.printStackTrace();
      }
    }

    if (!firstObject) {
      System.err.println("***                                                                                         ***");
      System.err.println("***********************************************************************************************");
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

  public THashSet<T> getRootObjects() {
    return myRootObjects;
  }

  public void addListener(ObjectTreeListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(ObjectTreeListener listener) {
    myListeners.remove(listener);
  }

  void fireRegistered(Object object) {
    for (ObjectTreeListener each : myListeners) {
      each.objectRegistered(object);
    }
  }

  void fireExecuted(Object object) {
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
      for (ObjectNode<T> node : parentNode.getChildren()) {
        T nodeObject = node.getObject();
        if (nodeObject.equals(object)) {
          return (D)nodeObject;
        }
      }
      return null;
    }
  }

  private static class MyTHashSet<T> extends THashSet<T> {
    private MyTHashSet() {
      super(IDENTITY);
    }

    public void compact() {
      if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
        super.compact();
      }
    }
  }

  public long getModification() {
    return myModification;
  }
}
