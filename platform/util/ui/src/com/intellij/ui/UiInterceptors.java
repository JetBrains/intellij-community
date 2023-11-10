// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class UiInterceptors {
  //interceptors used only for test purposes
  private static final Queue<UiInterceptor<?>> ourInterceptors = new ConcurrentLinkedQueue<>();

  // persistent interceptors used for redirecting real component display to different place.
  private static final Set<PersistentUiInterceptor<?>> persistentInterceptors = ConcurrentHashMap.newKeySet();

  /**
   * Called from a UI component
   *
   * @param uiComponent UI component which is about to be displayed
   * @return true if interception was successful, in this case no UI should be actually shown
   */
  public static boolean tryIntercept(@NotNull Object uiComponent) {
    return tryIntercept(uiComponent, null);
  }

  /**
   * Called from a UI component
   *
   * @param uiComponent UI component which is about to be displayed
   * @param relativePoint point where this ui component should be shown
   * @return true if interception was successful, in this case no UI should be actually shown
   */
  public static boolean tryIntercept(@NotNull Object uiComponent, @Nullable RelativePoint relativePoint) {
    UiInterceptor<?> interceptor = ourInterceptors.poll();
    if (interceptor != null) {
      interceptor.intercept(uiComponent);
      return true;
    }

    for (PersistentUiInterceptor<?> persistentUiInterceptor : persistentInterceptors) {
      if (persistentUiInterceptor.isApplicable(uiComponent, relativePoint)) {
        persistentUiInterceptor.intercept(uiComponent, relativePoint);
        return true;
      }
    }
    return false;
  }

  /**
   * Register interceptor to intercept next shown UI component
   *
   * @param interceptor interceptor to register
   */
  @TestOnly
  public static void register(@NotNull UiInterceptor<?> interceptor) {
    ourInterceptors.offer(interceptor);
  }

  /**
   * Register interceptor to intercept the next shown UI component, if the component isn't caught,
   * the interceptor will be removed on parent disposable termination, and no error will be produced
   *
   * @param parent on disposing, the interceptor will be removed from a collection
   * @param interceptor interceptor to register
   */
  @TestOnly
  public static void registerPossible(@NotNull Disposable parent, @NotNull UiInterceptor<?> interceptor) {
    ourInterceptors.offer(interceptor);
    Disposer.register(parent, () -> ourInterceptors.remove(interceptor));
  }

  public static void registerPersistent(@NotNull Disposable parent, @NotNull PersistentUiInterceptor<?> interceptor) {
    if (persistentInterceptors.add(interceptor)) {
      Disposer.register(parent, () -> persistentInterceptors.remove(interceptor));
    }
  }

  /**
   * Should be called in test tearDown to ensure that all registered interceptors were actually used.
   */
  @TestOnly
  public static void clear() {
    List<UiInterceptor<?>> interceptors = new ArrayList<>(ourInterceptors);
    ourInterceptors.clear();
    if (!interceptors.isEmpty()) {
      throw new IllegalStateException("Expected UI was not shown: " + interceptors);
    }
  }

  public abstract static class UiInterceptor<T> {
    protected final @NotNull Class<T> myClass;

    protected UiInterceptor(@NotNull Class<T> componentClass) {
      myClass = componentClass;
    }

    public final void intercept(@NotNull Object component) {
      if (!myClass.isInstance(component)) {
        throw new IllegalStateException("Unexpected UI component appears: wanted " + myClass.getName() + "; got: "
                                        + component.getClass().getName() + " (" + component + ")");
      }
      doIntercept(myClass.cast(component));
    }

    protected abstract void doIntercept(@NotNull T component);

    @Override
    public String toString() {
      return myClass.getName() + " (interceptor: " + getClass().getName() + ")";
    }
  }

  public abstract static class PersistentUiInterceptor<T> extends UiInterceptor<T> {
    protected PersistentUiInterceptor(@NotNull Class<T> componentClass) {
      super(componentClass);
    }

    public final boolean isApplicable(@NotNull Object component) {
      return isApplicable(component, null);
    }

    public final boolean isApplicable(@NotNull Object component, @Nullable RelativePoint relativePoint) {
      if (!myClass.isInstance(component)) {
        return false;
      }
      //noinspection unchecked
      return shouldIntercept((T)component, relativePoint);
    }

    public final void intercept(@NotNull Object component, @Nullable RelativePoint relativePoint) {
      if (!myClass.isInstance(component)) {
        throw new IllegalStateException("Unexpected UI component appears: wanted " + myClass.getName() + "; got: "
                                        + component.getClass().getName() + " (" + component + ")");
      }
      doIntercept(myClass.cast(component), relativePoint);
    }

    @Override
    public final void doIntercept(@NotNull T component) {
      intercept(component, null);
    }

    protected abstract void doIntercept(@NotNull T component, @Nullable RelativePoint owner);

    public abstract boolean shouldIntercept(@NotNull T component);

    public boolean shouldIntercept(@NotNull T component, @Nullable RelativePoint relativePoint) {
      return shouldIntercept(component);
    }
  }
}
