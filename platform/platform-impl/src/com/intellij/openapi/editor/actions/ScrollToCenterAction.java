// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ScrollToCenterAction extends InactiveEditorAction {
  public ScrollToCenterAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      EditorSettings settings = editor.getSettings();
      if (!settings.isRefrainFromScrolling()) {
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        return;
      }
      // The setting keeps a visible caret in place, so it is off for this scroll only.
      settings.setRefrainFromScrolling(false);
      try {
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
      finally {
        settings.resetRefrainFromScrolling();
        if (!settings.isRefrainFromScrolling()) {
          // The editor had its own value, and the default differs from it.
          settings.setRefrainFromScrolling(true);
        }
      }
    }
  }
}
