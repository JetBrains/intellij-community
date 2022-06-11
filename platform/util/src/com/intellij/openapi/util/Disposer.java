// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.Map;
import java.util.function.Predicate;

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

  /**
   * @return new {@link Disposable} unnamed instance
   */
  @NotNull
  @Contract(pure = true, value = "->new")
  public static Disposable newDisposable() {
    // must not be lambda because we care about identity in ObjectTree.myObject2NodeMap
    return new Disposable() {
      @Override
      public void dispose() { }

      @Override
      public String toString() {
        return "newDisposable";
      }
    };
  }

  /**
   * @return new {@link Disposable} instance with the given name which is visible in its {@link Disposable#toString()}.
   * Please be aware of increased memory consumption due to storing this name inside the object instance.
   */
  @NotNull
  @Contract(pure = true, value = "_->new")
  public static Disposable newDisposable(@NotNull @NonNls String debugName) {
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

  /**
   * @return new {@link Disposable} instance which tracks its own invalidation.
   * Please be aware of increased memory consumption due to storing extra flag for tracking invalidation.
   */
  @NotNull
  @Contract(pure = true, value = "->new")
  public static CheckedDisposable newCheckedDisposable() {
    return new CheckedDisposableImpl();
  }

  static class CheckedDisposableImpl implements CheckedDisposable {
    volatile boolean isDisposed;

    @Override
    public boolean isDisposed() {
      return isDisposed;
    }

    @Override
    public void dispose() {
      isDisposed = true;
    }

    @Override
    public String toString() {
      return "CheckedDisposableImpl{isDisposed=" + isDisposed + "} "+super.toString();
    }
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
   * @throws IncorrectOperationException If {@code child} has been registered with {@code parent} before;
   *                                     if {@code parent} is being disposed or already disposed ({@link #isDisposed(Disposable)}.
   */
  public static void register(@NotNull Disposable parent, @NotNull Disposable child) throws IncorrectOperationException {
    RuntimeException e = ourTree.register(parent, child);
    if (e != null) throw e;
  }

  /**
   * Registers child disposable under parent unless the parent has already been disposed
   * @return whether the registration succeeded
   */
  public static boolean tryRegister(@NotNull Disposable parent, @NotNull Disposable child) {
    return ourTree.register(parent, child) == null;
  }

  /**
   * @deprecated Use {@link #register(Disposable, Disposable)} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static void register(@NotNull Disposable parent, @NotNull Disposable child, @NonNls @NotNull final String key) {
    register(parent, child);
    Disposable v = get(key);
    if (v != null) throw new IllegalArgumentException("Key " + key + " already registered: " + v);
    ourKeyDisposables.put(key, child);
    register(child, new KeyDisposable(key));
  }

  private static final class KeyDisposable implements Disposable {
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

  /**
   * @return true if {@code disposable} is disposed or being disposed (i.e., its {@link Disposable#dispose()} method is executing).
   * @deprecated This method relies on relatively short-living diagnostic information which is cleared (to free up memory) on certain events,
   * for example on dynamic plugin unload or major GC run.<br/>
   * Thus, it's not wise to rely on this method in your production-grade code.<br/>
   * Instead, please
   * <li>Avoid using this method by registering your disposable in the parent disposable hierarchy with {@link #register(Disposable, Disposable)}</li> or, failing that,
   * <li>Use corresponding predicate inside the disposable object if available, i.e., {@link com.intellij.openapi.components.ComponentManager#isDisposed()} or</li>
   * <li>Introduce boolean flag into your object like this:
   * <pre> {@code class MyDisposable implements Disposable {
   *   boolean isDisposed;
   *   void dispose() {
   *     isDisposed = true;
   *   }
   *   boolean isDisposed() {
   *     return isDisposed;
   *   }
   * }}</pre> or</li>
   * <li>Use {@link #newCheckedDisposable()} (but be aware of increased memory consumption due to storing extra flag for tracking invalidation)</li>
   */
  @Deprecated
  public static boolean isDisposed(@NotNull Disposable disposable) {
    return ourTree.getDisposalInfo(disposable) != null;
  }

  /**
   * @deprecated use {@link #isDisposed(Disposable)} instead
   */
  @Deprecated
  public static boolean isDisposing(@NotNull Disposable disposable) {
    String message = "this method is deprecated and going to be removed soon. Please use isDisposed() instead";
    Logger.getInstance(Disposer.class).error(message);
    return isDisposed(disposable);
  }

  /**
   * @deprecated Store and use your own Disposable instead. Instead of {@code Disposer.get("ui")} use {@link com.intellij.openapi.application.ApplicationManager#getApplication()}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static Disposable get(@NotNull String key) {
    String message = "this method is deprecated and going to be removed soon. Store and use your own Disposable instead";
    Logger.getInstance(Disposer.class).error(message);
    return ourKeyDisposables.get(key);
  }

  public static void dispose(@NotNull Disposable disposable) {
    dispose(disposable, true);
  }

  /**
   * {@code predicate} is used only for direct children.
   */
  @ApiStatus.Internal
  public static void disposeChildren(@NotNull Disposable disposable, @Nullable Predicate<? super Disposable> predicate) {
    ourTree.executeAllChildren(disposable, predicate);
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

  /**
   * Returns stacktrace of the place where {@code disposable} was registered in {@code Disposer} or {@code null} if it's unknown. Works only
   * if {@link #setDebugMode debug mode} was enabled when {@code disposable} was registered.
   */
  @TestOnly
  public static @Nullable Throwable getRegistrationTrace(@NotNull Disposable disposable) {
    return getTree().getRegistrationTrace(disposable);
  }

  @ApiStatus.Internal
  public static void clearDisposalTraces() {
    ourTree.clearDisposedObjectTraces();
  }
}
