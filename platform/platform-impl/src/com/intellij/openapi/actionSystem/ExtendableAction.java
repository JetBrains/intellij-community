// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtendableAction extends AnAction {
  @NotNull private final ExtensionPointName<AnActionExtensionProvider> myExtensionPoint;

  public ExtendableAction(@NotNull ExtensionPointName<AnActionExtensionProvider> extensionPoint) {
    myExtensionPoint = extensionPoint;
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().copyFrom(getTemplatePresentation());

    commonUpdate(e);
    if (!e.getPresentation().isEnabled()) {
      return;
    }

    AnActionExtensionProvider provider = getProvider(e);
    if (provider != null) {
      ActionUpdateThread thread = provider.getActionUpdateThread();
      if (thread == ActionUpdateThread.BGT || EDT.isCurrentThreadEdt()) {
        provider.update(e);
      }
      else {
        e.getUpdateSession().compute(provider, "update", thread, () -> {
          provider.update(e);
          return true;
        });
      }
    }
    else {
      defaultUpdate(e);
    }
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    AnActionExtensionProvider provider = getProvider(e);
    if (provider != null) {
      provider.actionPerformed(e);
    }
    else {
      defaultActionPerformed(e);
    }
  }

  @Nullable
  private AnActionExtensionProvider getProvider(@NotNull AnActionEvent e) {
    return myExtensionPoint.findFirstSafe(provider -> provider.isActive(e));
  }

  protected void commonUpdate(@NotNull AnActionEvent e) {
  }

  protected void defaultUpdate(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  protected void defaultActionPerformed(@NotNull AnActionEvent e) {
  }
}
