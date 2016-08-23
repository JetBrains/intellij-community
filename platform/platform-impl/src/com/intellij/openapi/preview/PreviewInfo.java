/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.preview;

import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @param <V> is value type for data we show
 * @param <C> is "container" type that would be used/reused for displaying data
 */
public class PreviewInfo<V, C> {
  @NotNull private final PreviewPanelProvider<V, C> myProvider;
  @NotNull private final V myData;

  public static <V, C> PreviewInfo<V, C> create(@NotNull PreviewPanelProvider<V, C> provider, @NotNull V data) {
    return new PreviewInfo<>(provider, data);
  }

  public PreviewInfo(@NotNull PreviewPanelProvider<V, C> provider, @NotNull V data) {
    myProvider = provider;
    myData = data;
  }

  public PreviewProviderId<V, C> getId() {
    return myProvider.getId();
  }

  @NotNull
  public JComponent getComponent() {
    return myProvider.getComponent();
  }

  @NotNull
  public String getTitle() {
    return myProvider.getTitle(myData);
  }

  @NotNull
  public Icon getIcon() {
    Icon icon = myProvider.getIcon(myData);
    return icon != null ? icon : EmptyIcon.ICON_16;
  }

  @NotNull
  public PreviewPanelProvider<V, C> getProvider() {
    return myProvider;
  }

  @NotNull
  public V getData() {
    return myData;
  }

  @Nullable
  public C initComponent(boolean requestFocus) {
    return myProvider.initComponent(myData, requestFocus);
  }

  public boolean isModified(boolean beforeReuse) {
    return myProvider.isModified(myData, beforeReuse);
  }

  @Override
  public int hashCode() {
    return myProvider.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof PreviewInfo)) return false;
    //noinspection unchecked
    return ((PreviewInfo)obj).getId() == getId() && myProvider.contentsAreEqual( (V)((PreviewInfo)obj).myData, myData);
  }

  public void release() {
    myProvider.release(myData);
  }

  public boolean supportsStandardPlace() {
    return myProvider.supportsStandardPlace();
  }
}
