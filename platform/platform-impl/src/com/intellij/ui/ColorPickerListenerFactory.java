// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class ColorPickerListenerFactory {
  private static final ExtensionPointName<ColorPickerListenerFactory> EP_NAME =
    ExtensionPointName.create("com.intellij.colorPickerListenerFactory");

  public static @Unmodifiable @NotNull List<ColorPickerListener> createListenersFor(@Nullable PsiElement element) {
    List<ColorPickerListener> listeners = null;
    for (ColorPickerListenerFactory factory : EP_NAME.getExtensions()) {
      ColorPickerListener listener = factory.createListener(element);
      if (listener != null) {
        if (listeners == null) {
          listeners = new SmartList<>();
        }
        listeners.add(listener);
      }
    }
    return ContainerUtil.notNullize(listeners);
  }

  public abstract @Nullable ColorPickerListener createListener(@Nullable PsiElement element);
}
