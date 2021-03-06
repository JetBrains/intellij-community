// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ObjectTree {
  private static final ThreadLocal<Throwable> ourTopmostDisposeTrace = new ThreadLocal<>();

  // identity used here to prevent problems with hashCode/equals overridden by not very bright minds
  private final Set<Disposable> myRootObjects = new ReferenceOpenHashSet<>(); // guarded by treeLock
  // guarded by treeLock
  private final Map<Disposable, ObjectNode> myObject2NodeMap = new Reference2ObjectOpenHashMap<>();
  // Disposable -> trace or boolean marker (if trace unavailable)
  private final Map<Disposable, Object> myDisposedObjects = CollectionFactory.createWeakIdentityMap(100, 0.5f); // guarded by treeLock

  private final Object treeLock = new Object();

  private ObjectNode getNode(@NotNull Disposable object) {
    return myObject2NodeMap.get(object);
  }

  private void putNode(@NotNull Disposable object, @Nullable("null means remove") ObjectNode node) {
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
      node.getAndRemoveChildrenRecursively(disposables, predicate);
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
        node.getAndRemoveRecursively(disposables);
      }
      return disposables;
    });
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
        String message = "Memory leak detected: '" + object + "' of " + object.getClass() + " is registered in Disposer but wasn't disposed.\n" +
                         "Register it with a proper parentDisposable or ensure that it's always disposed by direct Disposer.dispose call.\n" +
                         "See https://jetbrains.org/intellij/sdk/docs/basics/disposers.html for more details.\n" +
                         "The corresponding Disposer.register() stacktrace is shown as the cause:\n";
        RuntimeException exception = new RuntimeException(message, trace);
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

  @Nullable
  <D extends Disposable> D findRegisteredObject(@NotNull Disposable parentDisposable, @NotNull D object) {
    synchronized (treeLock) {
      ObjectNode parentNode = getNode(parentDisposable);
      if (parentNode == null) return null;
      return parentNode.findChildEqualTo(object);
    }
  }

  void removeObjectFromTree(@NotNull ObjectNode node) {
    synchronized (treeLock) {
      Disposable myObject = node.getObject();
      putNode(myObject, null);
      ObjectNode parent = node.getParent();
      if (parent == null) {
        myRootObjects.remove(myObject);
      }
      else {
        parent.removeChild(node);
      }
      node.myParent = null;
    }
  }
}
