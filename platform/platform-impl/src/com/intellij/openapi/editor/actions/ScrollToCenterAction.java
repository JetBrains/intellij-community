// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
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
      boolean savedSetting = EditorSettingsExternalizable.getInstance().isRefrainFromScrolling();
      boolean overriddenInEditor = false;
      try {
        EditorSettingsExternalizable.getInstance().setRefrainFromScrolling(false);
        if (editor.getSettings().isRefrainFromScrolling()) {
          overriddenInEditor = true;
          editor.getSettings().setRefrainFromScrolling(false);
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
      finally {
        EditorSettingsExternalizable.getInstance().setRefrainFromScrolling(savedSetting);
        if (overriddenInEditor) {
          editor.getSettings().setRefrainFromScrolling(true);
        }
      }
    }
  }
}
