// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UiInterceptors {
  private static final Queue<UiInterceptor<?>> ourInterceptors = new ConcurrentLinkedQueue<>();

  /**
   * Called from UI component
   *
   * @param uiComponent UI component which is about to be displayed
   * @return true if interception was successful, in this case no UI should be actually shown
   */
  public static boolean tryIntercept(@NotNull Object uiComponent) {
    UiInterceptor<?> interceptor = ourInterceptors.poll();
    if (interceptor == null) return false;
    interceptor.intercept(uiComponent);
    return true;
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
    private final @NotNull Class<T> myClass;

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
      return myClass.getName()+" (interceptor: " + getClass().getName() + ")";
    }
  }
}
