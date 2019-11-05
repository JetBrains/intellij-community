// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Manages a parent-child relation of chained objects requiring cleanup.
 * <p/>
 * A root node can be created via {@link #newDisposable()} to which children are attached via subsequent calls to {@link #register(Disposable, Disposable)}.
 * Invoking {@link #dispose(Disposable)} will process all its registered children's {@link Disposable#dispose()} method.
 *
 * @see Disposable
 */
public class Disposer {
  private static final ObjectTree ourTree = new ObjectTree();

  public static boolean isDebugDisposerOn() {
    return "on".equals(System.getProperty("idea.disposer.debug"));
  }

  private static boolean ourDebugMode;

  private Disposer() {
  }

  @NotNull
  public static Disposable newDisposable() {
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return newDisposable(null);
  }

  @NotNull
  public static Disposable newDisposable(@Nullable String debugName) {
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return new Disposable() {
      @Override
      public void dispose() {
      }

      @Override
      public String toString() {
        return debugName == null ? super.toString() : debugName;
      }
    };
  }

  private static final Map<String, Disposable> ourKeyDisposables = ContainerUtil.createConcurrentWeakMap();

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

  public static void dispose(@NotNull Disposable disposable, boolean processUnregistered) {
    ourTree.executeAll(disposable, processUnregistered);
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
