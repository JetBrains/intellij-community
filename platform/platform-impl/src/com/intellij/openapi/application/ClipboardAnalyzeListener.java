// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.Patches;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClipboardAnalyzeListener implements ApplicationActivationListener {
  private static final int MAX_SIZE = 100 * 1024;
  private @Nullable String myCachedClipboardValue;

  @Override
  public void applicationActivated(final @NotNull IdeFrame ideFrame) {
    final Runnable processClipboard = () -> {
      final String clipboard = ClipboardUtil.getTextInClipboard();
      if (clipboard != null && clipboard.length() < MAX_SIZE && !clipboard.equals(myCachedClipboardValue)) {
        myCachedClipboardValue = clipboard;
        final Project project = ideFrame.getProject();
        if (project != null && !project.isDefault() && canHandle(myCachedClipboardValue)) {
          handle(project, myCachedClipboardValue);
        }
      }
    };

    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      //IDEA's clipboard is synchronized with the system clipboard on frame activation so we need to postpone clipboard processing
      new Alarm().addRequest(processClipboard, 300);
    }
    else {
      processClipboard.run();
    }
  }

  protected abstract void handle(@NotNull Project project, @NotNull String value);

  @Override
  public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    if (!ApplicationManager.getApplication().isDisposed()) {
      myCachedClipboardValue = ClipboardUtil.getTextInClipboard();
    }
  }

  public abstract boolean canHandle(@NotNull String value);
}
