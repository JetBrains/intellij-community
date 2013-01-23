/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.ListIterator;

public final class ObjectNode<T> {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private final ObjectTree<T> myTree;

  private ObjectNode<T> myParent;
  private final T myObject;

  private SmartList<ObjectNode<T>> myChildren;
  private final Throwable myTrace;

  private final long myOwnModification;

  public ObjectNode(@NotNull ObjectTree<T> tree, @Nullable ObjectNode<T> parentNode, @NotNull T object, long modification, @Nullable final Throwable trace) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = trace;
    myOwnModification = modification;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private ObjectNode<T>[] getChildrenArray() {
    synchronized (myTree.treeLock) {
      if (myChildren == null || myChildren.isEmpty()) return EMPTY_ARRAY;
      return myChildren.toArray(new ObjectNode[myChildren.size()]);
    }
  }

  void addChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      if (myChildren == null) {
        myChildren = new SmartList<ObjectNode<T>>();
      }
      myChildren.add(child);
      child.myParent = this;
    }
  }

  void removeChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      assert myChildren != null: "No children to remove child: " + this + ' ' + child;
      ListIterator<ObjectNode<T>> iterator = myChildren.listIterator(myChildren.size());
      while (iterator.hasPrevious()) {
        if (child.equals(iterator.previous())) {
          iterator.remove();
          return;
        }
      }
    }
  }

  public ObjectNode<T> getParent() {
    synchronized (myTree.treeLock) {
      return myParent;
    }
  }

  @NotNull
  public Collection<ObjectNode<T>> getChildren() {
    synchronized (myTree.treeLock) {
      if (myChildren == null) return Collections.emptyList();
      return Collections.unmodifiableCollection(myChildren);
    }
  }

  void execute(final boolean disposeTree, @NotNull final ObjectTreeAction<T> action) {
    ObjectTree.executeActionWithRecursiveGuard(this, myTree.getNodesInExecution(), new ObjectTreeAction<ObjectNode<T>>() {
      @Override
      public void execute(@NotNull ObjectNode<T> each) {
        try {
          action.beforeTreeExecution(myObject);
        }
        catch (Throwable t) {
          LOG.error(t);
        }

        ObjectNode<T>[] childrenArray = getChildrenArray();
        //todo: [kirillk] optimize
        for (int i = childrenArray.length - 1; i >= 0; i--) {
          childrenArray[i].execute(disposeTree, action);
        }

        if (disposeTree) {
          synchronized (myTree.treeLock) {
            myChildren = null;
          }
        }

        try {
          action.execute(myObject);
          myTree.fireExecuted(myObject);
        }
        catch (ProcessCanceledException e) {
          throw new ProcessCanceledException(e);
        }
        catch (Throwable e) {
          LOG.error(e);
        }

        if (disposeTree) {
          remove();
        }
      }

      @Override
      public void beforeTreeExecution(@NotNull ObjectNode<T> parent) {

      }
    });
  }

  private void remove() {
    myTree.putNode(myObject, null);
    synchronized (myTree.treeLock) {
      if (myParent == null) {
        myTree.removeRootObject(myObject);
      }
      else {
        myParent.removeChild(this);
      }
    }
  }

  @NotNull
  public T getObject() {
    return myObject;
  }

  @NonNls
  public String toString() {
    return "Node: " + myObject.toString();
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

  Throwable getAllocation() {
    return myTrace;
  }

  long getOwnModification() {
    return myOwnModification;
  }

  long getModification() {
    return getOwnModification();
  }

  <D extends Disposable> D findChildEqualTo(@NotNull D object) {
    synchronized (myTree.treeLock) {
      SmartList<ObjectNode<T>> children = myChildren;
      if (children != null) {
        for (ObjectNode<T> node : children) {
          T nodeObject = node.getObject();
          if (nodeObject.equals(object)) {
            return (D)nodeObject;
          }
        }
      }
      return null;
    }
  }
}
