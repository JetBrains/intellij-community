// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * <p>Manages a parent-child relation of chained objects requiring cleanup.</p>
 *
 * <p>A root node can be created via {@link #newDisposable()}, to which children are attached via subsequent calls to {@link #register(Disposable, Disposable)}.
 * Invoking {@link #dispose(Disposable)} will process all its registered children's {@link Disposable#dispose()} method.</p>
 * <p>
 * See <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/disposers.html">Disposer and Disposable</a> in SDK Docs.
 * @see Disposable
 */
public final class Disposer {
  private static final ObjectTree ourTree = new ObjectTree();

  public static boolean isDebugDisposerOn() {
    return "on".equals(System.getProperty("idea.disposer.debug"));
  }

  private static boolean ourDebugMode;

  private Disposer() { }

  @NotNull
  @Contract(pure = true, value = "->new")
  public static Disposable newDisposable() {
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return newDisposable("");
  }

  @NotNull
  @Contract(pure = true, value = "_->new")
  public static Disposable newDisposable(@NotNull String debugName) {
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return new Disposable() {
      @Override
      public void dispose() { }

      @Override
      public String toString() {
        return debugName;
      }
    };
  }

  @Contract(pure = true, value = "_,_->new")
  public static @NotNull Disposable newDisposable(@NotNull Disposable parentDisposable, @NotNull String debugName) {
    Disposable result = newDisposable(debugName);
    register(parentDisposable, result);
    return result;
  }

  private static final Map<String, Disposable> ourKeyDisposables = ContainerUtil.createConcurrentWeakMap();

  /**
   * Registers {@code child} so it is disposed right before its {@code parent}. See {@link Disposer class JavaDoc} for more details.
   * This overrides parent disposable for {@code child}, i.e. if {@code child} is already registered with {@code oldParent},
   * it's unregistered from {@code oldParent} before registering with {@code parent}.
   *
   * @throws com.intellij.util.IncorrectOperationException If {@code child} has been registered with {@code parent} before;
   *                                                       if {@code parent} is being disposed ({@link #isDisposing(Disposable)}) or
   *                                                       already disposed ({@link #isDisposed(Disposable)}.
   */
  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    ourTree.register(parent, child);
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child, @NonNls @NotNull final String key) {
    register(parent, child);
    Disposable v = get(key);
    if (v != null) throw new IllegalArgumentException("Key " + key + " already registered: " + v);
    ourKeyDisposables.put(key, child);
    register(child, new KeyDisposable(key));
  }

  private static class KeyDisposable implements Disposable {
    @NotNull
    private final String myKey;

    KeyDisposable(@NotNull String key) {myKey = key;}

    @Override
    public void dispose() {
      ourKeyDisposables.remove(myKey);
    }

    @Override
    public String toString() {
      return "KeyDisposable (" + myKey + ")";
    }
  }

  public static boolean isDisposed(@NotNull Disposable disposable) {
    return ourTree.getDisposalInfo(disposable) != null;
  }

  public static boolean isDisposing(@NotNull Disposable disposable) {
    return ourTree.isDisposing(disposable);
  }

  public static Disposable get(@NotNull String key) {
    return ourKeyDisposables.get(key);
  }

  public static void dispose(@NotNull Disposable disposable) {
    dispose(disposable, true);
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void disposeChildren(@NotNull Disposable disposable) {
    ourTree.executeAll(disposable, false, /* onlyChildren */ true);
  }

  public static void dispose(@NotNull Disposable disposable, boolean processUnregistered) {
    ourTree.executeAll(disposable, processUnregistered, /* onlyChildren */ false);
  }

  @NotNull
  public static ObjectTree getTree() {
    return ourTree;
  }

  public static void assertIsEmpty() {
    assertIsEmpty(false);
  }

  public static void assertIsEmpty(boolean throwError) {
    if (ourDebugMode) {
      ourTree.assertIsEmpty(throwError);
    }
  }

  /**
   * @return old value
   */
  public static boolean setDebugMode(boolean debugMode) {
    if (debugMode) {
      debugMode = !"off".equals(System.getProperty("idea.disposer.debug"));
    }
    boolean oldValue = ourDebugMode;
    ourDebugMode = debugMode;
    return oldValue;
  }

  public static boolean isDebugMode() {
    return ourDebugMode;
  }

  /**
   * @return object registered on {@code parentDisposable} which is equal to object, or {@code null} if not found
   */
  @Nullable
  public static <T extends Disposable> T findRegisteredObject(@NotNull Disposable parentDisposable, @NotNull T object) {
    return ourTree.findRegisteredObject(parentDisposable, object);
  }

  public static Throwable getDisposalTrace(@NotNull Disposable disposable) {
    return ObjectUtils.tryCast(getTree().getDisposalInfo(disposable), Throwable.class);
  }

  public static void clearDisposalTraces() {
    ourTree.clearDisposedObjectTraces();
  }
}