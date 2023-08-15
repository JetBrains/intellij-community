// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.content.impl.ContentManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ContentFactoryImpl implements ContentFactory {
  @Override
  public @NotNull ContentImpl createContent(JComponent component, @Nullable @NlsContexts.TabTitle String displayName, boolean isLockable) {
    return new ContentImpl(component, displayName, isLockable);
  }

  @Override
  public @NotNull ContentManagerImpl createContentManager(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project) {
    return new ContentManagerImpl(contentUI, canCloseContents, project);
  }

  @Override
  public @NotNull ContentManager createContentManager(boolean canCloseContents, @NotNull Project project) {
    return createContentManager(new TabbedPaneContentUI(), canCloseContents, project);
  }
}
