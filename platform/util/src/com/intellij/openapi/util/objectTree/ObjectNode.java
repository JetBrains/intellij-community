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
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public final class ObjectNode<T> {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");
  private static final ArrayFactory<ObjectNode> OBJECT_NODE_ARRAY_FACTORY = new ArrayFactory<ObjectNode>() {
    @Override
    public ObjectNode[] create(int count) {
      return new ObjectNode[count];
    }
  };

  private final ObjectTree<T> myTree;

  private ObjectNode<T> myParent;
  private final T myObject;

  @Nullable
  private ObjectNode<T>[] myChildren;
  private int myChildrenSize;

  private final Throwable myTrace;

  private final long myOwnModification;

  public ObjectNode(@NotNull ObjectTree<T> tree, @Nullable ObjectNode<T> parentNode, @NotNull T object, long modification, @Nullable final Throwable trace) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = trace;
    myOwnModification = modification;
  }

  void addChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      ObjectNode<T>[] children = myChildren;
      if (children == null) {
        //noinspection unchecked
        children = new ObjectNode[]{child};
      }
      else {
        int capacity = children.length;
        if (myChildrenSize >= capacity) {
          int newSize;
          // for small arrays do not waste memory, since most of nodes have few children.
          if (capacity < 10) {
            newSize = capacity + 1;
          }
          else {
            newSize = capacity * 3 / 2;
          }
          //noinspection unchecked
          children = ArrayUtil.realloc(children, newSize, OBJECT_NODE_ARRAY_FACTORY);
        }
        children[myChildrenSize] = child;
      }
      myChildren = children;
      myChildrenSize++;
      child.myParent = this;
    }
  }

  void removeChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      ObjectNode<T>[] children = myChildren;
      assert children != null: "No children to remove child: " + this + ' ' + child;
      if (myChildrenSize == 1) {
        if (child.equals(children[0])) {
          myChildren = null;
        }
      }
      else {
        // optimization: find in reverse order because execute() iterates children backwards
        int idx;
        for (idx = myChildrenSize - 1; idx >= 0; idx--) {
          ObjectNode<T> node = children[idx];
          if (node == child) break;
        }
        if (idx == -1) return;
        if (idx != children.length - 1) {
          System.arraycopy(children, idx+1, children, idx, children.length - idx - 1);
        }
        children[myChildrenSize-1] = null;
      }
      myChildrenSize--;
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
      ObjectNode<T>[] children = myChildren;
      if (children == null) return Collections.emptyList();
      //noinspection unchecked
      return Arrays.asList(children);
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

        ObjectNode<T>[] childrenArray;
        int size;
        synchronized (myTree.treeLock) {
          ObjectNode<T>[] children = myChildren;
          //noinspection unchecked
          childrenArray = children == null ? EMPTY_ARRAY : children;
          size = myChildrenSize;
        }
        for (int i = size - 1; i >= 0; i--) {
          childrenArray[i].execute(disposeTree, action);
        }

        if (disposeTree) {
          synchronized (myTree.treeLock) {
            myChildren = null;
            myChildrenSize = 0;
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
        for (int i = 0; i < myChildrenSize; i++) {
          ObjectNode<T> node = myChildren[i];
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
      ObjectNode<T>[] children = myChildren;
      if (children != null) {
        for (int i = 0; i < myChildrenSize; i++) {
          ObjectNode<T> node = children[i];
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
