/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ksafonov
 */
public abstract class ColorPickerListenerFactory {
  private static final ExtensionPointName<ColorPickerListenerFactory> EP_NAME =
    ExtensionPointName.create("com.intellij.colorPickerListenerFactory");

  public static ColorPickerListener[] createListenersFor(@Nullable final PsiElement element) {
    final List<ColorPickerListener> listeners =
      ContainerUtil.mapNotNull(EP_NAME.getExtensions(), new Function<ColorPickerListenerFactory, ColorPickerListener>() {
        @Override
        public ColorPickerListener fun(ColorPickerListenerFactory factory) {
          return factory.createListener(element);
        }
      });
    return listeners.toArray(new ColorPickerListener[listeners.size()]);
  }

  public abstract ColorPickerListener createListener(@Nullable PsiElement element);
}
