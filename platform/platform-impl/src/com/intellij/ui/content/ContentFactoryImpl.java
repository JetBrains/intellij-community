// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.content.impl.ContentManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ContentFactoryImpl implements ContentFactory {
  @NotNull
  @Override
  public ContentImpl createContent(JComponent component, @Nullable @NlsContexts.TabTitle String displayName, boolean isLockable) {
    return new ContentImpl(component, displayName, isLockable);
  }

  @NotNull
  @Override
  public ContentManagerImpl createContentManager(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project) {
    return new ContentManagerImpl(contentUI, canCloseContents, project);
  }

  @NotNull
  @Override
  public ContentManager createContentManager(boolean canCloseContents, @NotNull Project project) {
    return createContentManager(new TabbedPaneContentUI(), canCloseContents, project);
  }
}
