// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.EventListener;

import static com.intellij.util.ui.UIUtil.uiTraverser;

/**
 * Pushes events down the UI components hierarchy.
 *
 * @author gregsh
 */
public final class ComponentTreeEventDispatcher<T extends EventListener> {

  private final Class<T> myListenerClass;
  private final T myMulticaster;

  public static <T extends EventListener> ComponentTreeEventDispatcher<T> create(@NotNull Class<T> listenerClass) {
    return create(null, listenerClass);
  }

  public static <T extends EventListener> ComponentTreeEventDispatcher<T> create(@Nullable Component root, @NotNull Class<T> listenerClass) {
    return new ComponentTreeEventDispatcher<>(root, listenerClass);
  }

  private ComponentTreeEventDispatcher(final @Nullable Component root, @NotNull Class<T> listenerClass) {
    myListenerClass = listenerClass;
    myMulticaster = EventDispatcher.createMulticaster(listenerClass, null, () -> {
      JBTreeTraverser<Component> traverser = uiTraverser(root);
      if (root == null) traverser = traverser.withRoots(Arrays.asList(Window.getWindows()));
      return traverser.postOrderDfsTraversal().filter(myListenerClass);
    });
  }

  public @NotNull T getMulticaster() {
    return myMulticaster;
  }

}
