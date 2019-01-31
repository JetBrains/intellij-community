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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ObjectNode<T> {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private final ObjectTree<T> myTree;

  private ObjectNode<T> myParent; // guarded by myTree.treeLock
  private final T myObject;

  private List<ObjectNode<T>> myChildren; // guarded by myTree.treeLock
  private final Throwable myTrace;

  private final long myOwnModification;

  ObjectNode(@NotNull ObjectTree<T> tree,
             @Nullable ObjectNode<T> parentNode,
             @NotNull T object,
             long modification) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = parentNode == null && Disposer.isDebugMode() ? ThrowableInterner.intern(new Throwable()) : null;
    myOwnModification = modification;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private ObjectNode<T>[] getChildrenArray() {
    List<ObjectNode<T>> children = myChildren;
    if (children == null || children.isEmpty()) return EMPTY_ARRAY;
    return children.toArray(new ObjectNode[0]);
  }

  void addChild(@NotNull ObjectNode<T> child) {
    List<ObjectNode<T>> children = myChildren;
    if (children == null) {
      myChildren = new SmartList<ObjectNode<T>>(child);
    }
    else {
      children.add(child);
    }
    child.myParent = this;
  }

  void removeChild(@NotNull ObjectNode<T> child) {
    List<ObjectNode<T>> children = myChildren;
    if (children != null) {
      // optimisation: iterate backwards
      for (int i = children.size() - 1; i >= 0; i--) {
        ObjectNode<T> node = children.get(i);
        if (node.equals(child)) {
          children.remove(i);
          break;
        }
      }
    }
    child.myParent = null;
  }

  ObjectNode<T> getParent() {
    return myParent;
  }

  @NotNull
  Collection<ObjectNode<T>> getChildren() {
    synchronized (myTree.treeLock) {
      if (myChildren == null) return Collections.emptyList();
      return Collections.unmodifiableCollection(myChildren);
    }
  }

  void execute(@NotNull final ObjectTreeAction<T> action, @NotNull final List<Throwable> exceptions) {
    ObjectTree.executeActionWithRecursiveGuard(this, myTree.getNodesInExecution(), new ObjectTreeAction<ObjectNode<T>>() {
      @Override
      public void execute(@NotNull ObjectNode<T> each) {
        if (myTree.getDisposalInfo(myObject) != null) return; // already disposed. may happen when someone does `register(obj, ()->Disposer.dispose(t));` abomination
        try {
          action.beforeTreeExecution(myObject);
        }
        catch (Throwable t) {
          LOG.error(t);
        }

        ObjectNode<T>[] childrenArray;
        synchronized (myTree.treeLock) {
          childrenArray = getChildrenArray();
          myChildren = null;
        }

        for (int i = childrenArray.length - 1; i >= 0; i--) {
          try {
            ObjectNode<T> childNode = childrenArray[i];
            childNode.execute(action, exceptions);
            synchronized (myTree.treeLock) {
              childNode.myParent = null;
            }
          }
          catch (Throwable e) {
            exceptions.add(e);
          }
        }

        try {
          action.execute(myObject);
          myTree.fireExecuted(myObject);
        }
        catch (Throwable e) {
          exceptions.add(e);
        }
        removeFromObjectTree();

        handleExceptions(exceptions);
      }

      @Override
      public void beforeTreeExecution(@NotNull ObjectNode<T> parent) {
      }
    });
  }

  private static void handleExceptions(List<Throwable> exceptions) {
    if (!exceptions.isEmpty()) {
      for (Throwable exception : exceptions) {
        if (!(exception instanceof ProcessCanceledException)) {
          LOG.error(exception);
        }
      }

      ProcessCanceledException pce = ContainerUtil.findInstance(exceptions, ProcessCanceledException.class);
      if (pce != null) {
        throw pce;
      }
      exceptions.clear();
    }
  }

  void removeFromObjectTree() {
    synchronized (myTree.treeLock) {
      myTree.putNode(myObject, null);
      if (myParent == null) {
        myTree.removeRootObject(myObject);
      }
    }
  }

  @NotNull
  T getObject() {
    return myObject;
  }

  @Override
  @NonNls
  public String toString() {
    return "Node: " + myObject;
  }

  Throwable getTrace() {
    return myTrace;
  }

  @TestOnly
  void assertNoReferencesKept(@NotNull T aDisposable) {
    assert getObject() != aDisposable;
    synchronized (myTree.treeLock) {
      if (myChildren != null) {
        for (ObjectNode<T> node: myChildren) {
          node.assertNoReferencesKept(aDisposable);
        }
      }
    }
  }

  long getOwnModification() {
    return myOwnModification;
  }

  long getModification() {
    return getOwnModification();
  }

  <D extends Disposable> D findChildEqualTo(@NotNull D object) {
    synchronized (myTree.treeLock) {
      List<ObjectNode<T>> children = myChildren;
      if (children != null) {
        for (ObjectNode<T> node : children) {
          T nodeObject = node.getObject();
          if (nodeObject.equals(object)) {
            //noinspection unchecked
            return (D)nodeObject;
          }
        }
      }
      return null;
    }
  }
}
