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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PreviewPanelProvider<V, C> implements Disposable {
  public static final ExtensionPointName<PreviewPanelProvider> EP_NAME = new ExtensionPointName<>("com.intellij.previewPanelProvider");
  private final PreviewProviderId<V, C> myId;

  public PreviewPanelProvider(PreviewProviderId<V, C> id) {
    myId = id;
  }

  @NotNull
  public final PreviewProviderId<V, C> getId() {
    return myId;
  }

  //Let's share single specific component for all provided previews (if possible)
  @NotNull
  protected abstract JComponent getComponent();

  @NotNull
  protected abstract String getTitle(@NotNull V content);

  @Nullable
  protected abstract Icon getIcon(@NotNull V content);

  public abstract float getMenuOrder();

  public abstract void showInStandardPlace(@NotNull V content);

  @Nullable protected abstract C initComponent(V content, boolean requestFocus);

  public abstract boolean isModified(V content, boolean beforeReuse);

  public abstract void release(@NotNull V content);

  public abstract boolean contentsAreEqual(@NotNull V content1, @NotNull V content2);

  @Override
  public final String toString() {
    return myId.getVisualName();
  }

  public boolean supportsStandardPlace() {
    return true;
  }
}
