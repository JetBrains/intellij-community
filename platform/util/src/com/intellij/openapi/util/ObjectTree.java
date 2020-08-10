// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Supplier;

final class ObjectTree {
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<Disposable> myRootObjects = new ReferenceOpenHashSet<>(); // guarded by treeLock
  // guarded by treeLock
  private final Map<Disposable, ObjectNode> myObject2NodeMap = new Reference2ObjectOpenHashMap<>();
  // Disposable -> trace or boolean marker (if trace unavailable)
  private final Map<Disposable, Object> myDisposedObjects = ContainerUtil.createWeakMap(100, 0.5f, ContainerUtil.identityStrategy()); // guarded by treeLock

  // disposables for which Disposer.dispose() is currently running
  final List<Disposable> myObjectsBeingDisposed = new ArrayList<>(); // guarded by myObjectsBeingDisposed

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

  @Nullable
  final RuntimeException register(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) return new IllegalArgumentException("Cannot register to itself: "+parent);
    synchronized (treeLock) {
      Object wasDisposed = getDisposalInfo(parent);
      if (wasDisposed != null) {
        return new IncorrectOperationException("Sorry but parent: " + parent + " has already been disposed " +
                                              "(see the cause for stacktrace) so the child: "+child+" will never be disposed",
                                              wasDisposed instanceof Throwable ? (Throwable)wasDisposed : null);
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

      RuntimeException e = checkWasNotAddedAlready(parentNode, childNode);
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

  private static RuntimeException checkWasNotAddedAlready(@NotNull ObjectNode childNode, @NotNull ObjectNode parentNode) {
    for (ObjectNode node = childNode; node != null; node = node.getParent()) {
      if (node == parentNode) {
        return new IncorrectOperationException("'"+childNode.getObject() + "' was already added as a child of '" + parentNode.getObject()+"'");
      }
    }
    return null;
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
        List<Throwable> exceptions = node.execute(null, onlyChildren);
        if (parent != null && !onlyChildren) {
          synchronized (treeLock) {
            parent.removeChild(node);
          }
        }
        if (exceptions != null) {
          handleExceptions(exceptions);
        }
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

  boolean isDisposing(@NotNull Disposable disposable) {
    synchronized (myObjectsBeingDisposed) {
      if (ContainerUtil.indexOfIdentity(myObjectsBeingDisposed, disposable) != -1) {
        return true;
      }
    }
    return false;
  }

  List<Throwable> executeActionWithRecursiveGuard(@NotNull Disposable object, @NotNull Supplier<? extends List<Throwable>> action) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (myObjectsBeingDisposed) {
      int i = ContainerUtil.lastIndexOfIdentity(myObjectsBeingDisposed, object);
      if (i != -1) {
        return null;
      }
      myObjectsBeingDisposed.add(object);
    }

    try {
      return action.get();
    }
    finally {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (myObjectsBeingDisposed) {
        int i = ContainerUtil.lastIndexOfIdentity(myObjectsBeingDisposed, object);
        assert i != -1;
        myObjectsBeingDisposed.remove(i);
      }
    }
  }

  private void executeUnregistered(@NotNull Disposable disposable) {
    List<Throwable> exceptions = executeActionWithRecursiveGuard(disposable, ()-> {
      try {
        //noinspection SSBasedInspection
        disposable.dispose();
        return null;
      }
      catch (Throwable e) {
        return Collections.singletonList(e);
      }
    });
    if (exceptions != null) {
      handleExceptions(exceptions);
    }
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
    synchronized (treeLock) {
      myDisposedObjects.clear();
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
